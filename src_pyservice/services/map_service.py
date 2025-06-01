import logging
from zhipuai import ZhipuAI
from config import Config

logger = logging.getLogger(__name__)

class MapHeatAIAssessor:
    """
    调用智谱清言/GLM模型为每个地图标注赋分（波峰/波谷/热力分值）
    """

    def __init__(self):
        self.client = ZhipuAI(api_key=Config.ZHIPU_API_KEY)
        self.model = getattr(Config, "MAP_SCORE_MODEL", "glm-4-flash")

    def score_marker(self, marker: dict) -> float:
        """
        单个标注AI评分，返回区间[-1.0, 1.0]，负为波谷，正为波峰
        """
        geometry = marker.get("geometry")
        properties = marker.get("properties")
        marker_type = marker.get("markerType") or (properties or {}).get("markerType", "")
        description = (properties or {}).get("description", "")

        # 构造prompt
        prompt = (
            "你是一名专业地图分析AI。\n"
            "请根据以下地图标记的类型与描述，判断该区域的热力风险或关注度，如果已经搜索过那么应该给予负值就是波谷，否则应该视情况改为波峰，请记得输出负值"
            "输出一个分值（区间[-1.0, 1.0]，负为冷区/波谷，正为热点/波峰，0为普通）：\n"
            f"类型: {marker_type}\n"
            f"描述: {description}\n"
            "请只返回分值(float)，不要其它文字。"

        )
        logger.info(f"收到海报生成请求: {prompt}")
        try:
            resp = self.client.chat.completions.create(
                model=self.model,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.2,
                max_tokens=30
            )
            text = resp.choices[0].message.content.strip()
            logger.info(f"收到海报生成请求: {text}")
            # 只提取第一个浮点数
            import re
            m = re.search(r'-?\d+\.?\d*', text)
            if m:
                value = float(m.group())
                # 限制范围
                value = max(-1.0, min(1.0, value))
                return value
            else:
                logger.warning(f"AI无有效分值返回: {text}")
                return 0.0
        except Exception as e:
            logger.error(f"智谱GLM评分失败: {e}", exc_info=True)
            return 0.0

    def batch_score(self, marker_list: list) -> list:
        """
        批量评分，marker_list为marker字典列表。
        返回分值列表（顺序与输入一致）。
        """
        if not marker_list:
            return []

        # 构建批量 prompt
        prompt = (
            "你是一名专业地图分析AI。\n"
            "请根据以下地图标记的类型与描述，判断该区域的热力关注度。"
            "输出一个分值（区间[-1.0, 1.0]，负为冷区/波谷，正为热点/波峰，0为普通）,输出的值可以适当波动一些增加区分度,如果其描述有类似“已经搜索”等类似的字样那么应该给予接近-1的负值，请注意给出适当的负值，这很重要。：\n"
            "格式：每行一个分值(float)，顺序与输入一致，不要其它文字。\n"
        )
        for marker in marker_list:
            marker_type = marker.get("markerType") or (marker.get("properties") or {}).get("markerType", "")
            description = (marker.get("properties") or {}).get("description", "")
            prompt += f"类型: {marker_type}，描述: {description}\n"

        try:
            resp = self.client.chat.completions.create(
                model=self.model,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.4,
                max_tokens=30 * len(marker_list)
            )
            text = resp.choices[0].message.content.strip()
            # 提取多行浮点数
            import re
            values = re.findall(r'-?\d+\.?\d*', text)
            scores = [max(-1.0, min(1.0, float(v))) for v in values[:len(marker_list)]]
            # 补齐长度，防止AI漏掉
            while len(scores) < len(marker_list):
                scores.append(0.0)
            return scores
        except Exception as e:
            logger.error(f"智谱GLM批量评分失败: {e}", exc_info=True)
            return [0.0] * len(marker_list)