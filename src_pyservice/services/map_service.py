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
            "你是一名专业地图分析AI。我们正在执行一项搜救走失老人的任务，搜救员会将搜索过的区域在地图上进行标记。每个标记有类型和描述，请根据规则为每个标记评分：\n"
            "【标记类型说明】\n"
            "- searched：已搜索区域，通常代表该区域已经被搜查过，优先级低，通常不再需要更多人力。\n"
            "- danger：危险区域，代表这里有危险或需重点关注，通常需要较多的人力资源。也可能由于过于危险，搜救对象不太可能在这里出没\n"
            "- poi：兴趣点，通常是需要关注、可能有被困人员的区域，优先级较高。\n"
            "- path：搜索路径，表示已规划或已执行的搜索线路，通常优先级较低。\n"
            "【评分规则】\n"
            "- 分值区间为[-1.0, 1.0]。\n"
            "- 已搜索区域、已覆盖区域、描述中包含“已搜索”“已排查”等字样或者其他表示该地方不太可能会出现搜救目标的描述，评分应靠近-1.0（负值）。\n"
            "- 需要重点关注或人力的区域，评分应靠近1.0（正值）。\n"
            "- 兴趣点（poi）也应给予正值，未搜索区域也给正值。\n"
            "- 路径（path）类型一般给负值或接近0。\n"
            "- 普通或不明确区域可以给0。\n"
            "- 分值可以适当波动，体现不同标记的细微差别。\n"
            "【输出格式】\n"
            "每行一个分值（float），顺序与输入一致，不要输出其它文字。\n"
            "请根据以上类型说明和规则并附加你对描述的个人理解，对以下标记进行评分：\n"
        )

        for marker in marker_list:
            marker_type = marker.get("markerType") or (marker.get("properties") or {}).get("markerType", "")
            description = (marker.get("properties") or {}).get("description", "")
            prompt += f"类型: {marker_type}，描述: {description}\n"

        try:
            resp = self.client.chat.completions.create(
                model=self.model,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1,
                max_tokens=70 * len(marker_list)
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