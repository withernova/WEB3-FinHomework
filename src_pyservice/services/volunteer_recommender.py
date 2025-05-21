import logging
from typing import List, Tuple
from haversine import haversine
from zhipuai import ZhipuAI
from sqlalchemy import text
from utils.db import SessionLocal
from config import Config

logger = logging.getLogger(__name__)

class Volunteer:
    """简易数据载体，不做 ORM 绑定以保持独立"""
    def __init__(self, uuid, name, lat, lon, tags, success_cnt):
        self.uuid = uuid
        self.name = name
        self.lat  = lat
        self.lon  = lon
        self.tags = tags or []
        self.success_cnt = success_cnt or 0

class VolunteerRecommender:
    """核心：① 从库取数据 ② 打分排序 ③ 生成报告"""

    def __init__(self):
        self.client = ZhipuAI(api_key=Config.ZHIPU_API_KEY)

    # ---------- 1. 取数据 ----------
    def _fetch_volunteers(self) -> List[Volunteer]:
        """
        从 rescuers 表中拉取“available”志愿者
        假设表字段：uuid, name, latitude, longitude,
                   skill_tags(JSON 列), success_count(INT)
        """
        sql = text("""
            SELECT uuid,name,latitude,longitude,
                   skill_tags,success_count
            FROM rescuers
            WHERE status='available'
        """)
        with SessionLocal() as db:
            rows = db.execute(sql).mappings().all()

        volunteers = [
            Volunteer(
                r["uuid"], r["name"],
                float(r["latitude"]), float(r["longitude"]),
                r["skill_tags"] or [],
                int(r["success_count"] or 0)
            )
            for r in rows
        ]
        return volunteers

    # ---------- 2. 打分 ----------
    def _score(self,
               v: Volunteer,
               lost_lat: float,
               lost_lon: float,
               required_tags: List[str],
               max_dist_km: float,
               max_success: int) -> float:

        dist = haversine((lost_lat, lost_lon), (v.lat, v.lon))  # km
        dist_score = 1 - min(dist, max_dist_km)/max_dist_km     # 距离越近分越高

        tag_overlap = len(set(required_tags) & set(v.tags))
        tag_score   = tag_overlap / max(1, len(required_tags))

        success_score = v.success_cnt / max(1, max_success)

        total = (Config.WEIGHT_DISTANCE * dist_score +
                 Config.WEIGHT_TAG      * tag_score   +
                 Config.WEIGHT_SUCCESS  * success_score)

        logger.debug(
            f"{v.name} dist={dist:.1f}km→{dist_score:.2f}, "
            f"tag={tag_overlap}/{len(required_tags)}→{tag_score:.2f}, "
            f"succ={v.success_cnt}/{max_success}→{success_score:.2f}, "
            f"TOTAL={total:.3f}"
        )
        return total

    def pick_top3(self,
                  lost_lat: float,
                  lost_lon: float,
                  required_tags: List[str]) -> List[Volunteer]:
        volunteers = self._fetch_volunteers()
        if not volunteers:
            return []

        max_dist_km = max(
            haversine((lost_lat, lost_lon), (v.lat, v.lon))
            for v in volunteers
        ) or 1
        max_success = max(v.success_cnt for v in volunteers) or 1

        scored: List[Tuple[Volunteer,float]] = []
        for v in volunteers:
            s = self._score(v, lost_lat, lost_lon,
                            required_tags, max_dist_km, max_success)
            scored.append((v, s))

        scored.sort(key=lambda x: x[1], reverse=True)
        top3 = [v for v, _ in scored[:3]]
        return top3

    # ---------- 3. 生成报告 ----------
    def gen_report(self,
                   family_name: str,
                   lost_address: str,
                   lost_lat: float,
                   lost_lon: float,
                   tags: List[str]) -> str:

        top3 = self.pick_top3(lost_lat, lost_lon, tags)
        if len(top3) < 3:
            logger.warning("可选志愿者不足 3 人，返回空列表")
            return "当前可用志愿者不足，无法生成推荐报告。"

        # 拼模板 → 发送到 LLM，让模型润色（可选）
        def format_one(idx, v: Volunteer, lost_lat, lost_lon):
            dist_km = haversine((lost_lat,lost_lon),(v.lat,v.lon))
            return (
                f"志愿者{idx}：{v.name}\n"
                f"当前位置：距失踪地约{dist_km:.1f}公里\n"
                f"兴趣标签：{' '.join('#'+t for t in v.tags[:5])}\n"
                f"个人简介：待补充\n"  # 若 rescuer 表有 profile 可替换
            )
        body = "\n".join(
            format_one(i+1,v,lost_lat,lost_lon) for i,v in enumerate(top3)
        )

        prompt = (
f"""请把下面的要点生成正式中文公文格式的“寻人志愿者推荐报告”，语气参考示例。

要点：
家庭称呼：{family_name if family_name else '尊敬的家属'}
失踪地点：{lost_address}
引用志愿者列表：
{body}

要求：
1. 标题“寻人志愿者推荐报告”居中
2. 署名“寻人平台志愿者协调组”，末尾保留“日期：{{date}}”占位符
3. 中间正文与示例保持同样逻辑段落
"""
        )

        resp = self.client.chat.completions.create(
            model=Config.MODEL_NAME,
            messages=[
                {"role":"system","content":"你是一名公文写作专家。"},
                {"role":"user","content":prompt}
            ]
        )
        report = resp.choices[0].message.content.strip()
        return report
