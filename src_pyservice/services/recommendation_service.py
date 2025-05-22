# services/recommendation_service.py
from zhipuai import ZhipuAI
from config import Config
from services.geo_service import GeoService
import logging
import json

logger = logging.getLogger(__name__)

class RecommendationService:
    def __init__(self, api_key=None, model=None):
        self.api_key = api_key or Config.ZHIPU_API_KEY
        self.model = model or Config.MODEL_NAME
        self.client = ZhipuAI(api_key=self.api_key)
        self.geo_service = GeoService()
    
    def generate_recommendations(self, task, rescuers):
        """
        生成救援人员推荐
        
        Args:
            task (dict): 任务信息
            rescuers (list): 救援人员列表
        
        Returns:
            dict: 推荐结果
        """
        try:
            # 检查必要字段
            if not task.get("location") or not task.get("id") or not task.get("elderName"):
                return {"success": False, "message": "任务信息不完整"}
            
            if not rescuers or len(rescuers) == 0:
                return {"success": False, "message": "没有可用的救援人员"}
            
            # 计算每个救援人员的得分
            scored_rescuers = []
            for rescuer in rescuers:
                # 检查必要字段
                if not rescuer.get("uuid") or not rescuer.get("name") or not rescuer.get("location"):
                    continue
                
                # 计算距离分数
                distance = self.geo_service.calculate_distance(
                    task["location"], 
                    rescuer["location"]
                )
                
                # 如果无法计算距离，设置默认值
                if distance is None:
                    distance = 10.0  # 默认10公里
                    distance_score = 5.0  # 默认中等分数
                else:
                    # 距离越近，分数越高，最高10分
                    distance_score = max(0, 10 - distance / 5)  # 每5公里减1分
                
                # 计算技能标签匹配度
                skill_tags = rescuer.get("skillTags", [])
                tag_score, tag_reason = self.calculate_tag_match(task, skill_tags)
                
                # 计算历史成功任务分数
                successful_tasks = 0
                if "taskIds" in rescuer and rescuer["taskIds"]:
                    # 这里简化处理，实际应该通过查询任务状态来计算
                    successful_tasks = rescuer.get("successfulTasks", 0)
                
                # 历史任务分数 - 每个成功任务2分，上限10分
                success_score = min(10, successful_tasks * 2)
                
                # 计算总分 - 平均加权
                total_score = (distance_score + tag_score + success_score) / 3
                
                # 添加到结果列表
                scored_rescuers.append({
                    "uuid": rescuer["uuid"],
                    "name": rescuer["name"],
                    "phone": rescuer.get("phone", "未提供"),  # 确保保留电话号码
                    "distance": distance,
                    "distance_score": distance_score,
                    "tag_score": tag_score,
                    "tag_reason": tag_reason,
                    "success_score": success_score,
                    "successful_tasks": successful_tasks,
                    "total_score": total_score,
                    "skill_tags": skill_tags
                })
            
            # 排序并选择前三名
            top_rescuers = sorted(scored_rescuers, key=lambda x: x["total_score"], reverse=True)[:3]
            
            if not top_rescuers:
                return {"success": False, "message": "没有合适的救援人员推荐"}
            
            # 生成报告
            report_html, report_markdown = self.generate_recommendation_report(task, top_rescuers)
            
            return {
                "success": True,
                "top_rescuers": top_rescuers,
                "report_html": report_html,
                "report_markdown": report_markdown,
                "report_text": report_markdown  # 保持与Java端兼容
            }
        
        except Exception as e:
            logger.error(f"生成推荐时发生错误: {str(e)}", exc_info=True)
            return {"success": False, "message": f"生成推荐时发生错误: {str(e)}"}
    
    def calculate_tag_match(self, task, rescuer_tags):
        """
        计算技能标签与任务的匹配度
        
        Args:
            task (dict): 任务信息
            rescuer_tags (list): 救援人员技能标签
        
        Returns:
            tuple: (匹配分数, 匹配原因)
        """
        try:
            if not rescuer_tags:
                return 5.0, "无技能标签信息，给予中等评分"
            
            # 构建提示词
            prompt = f"""
            任务需求：
            老人姓名：{task.get('elderName', '未知')}
            走失地点：{task.get('location', '未知')}
            额外信息：{task.get('extraInfo', '无')}
            
            救援人员技能标签：{', '.join(rescuer_tags)}
            
            请评估这些技能标签与走失老人救援任务需求的匹配程度，给出一个0到10的评分，并详细说明原因。
            
            请使用以下JSON格式返回结果：
            {{"score": [评分], "reason": "[原因]"}}
            """
            
            # 调用智谱AI模型
            response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": "你是一个专业的救援任务分配系统，善于分析任务需求与救援人员能力的匹配度。"},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=300
            )
            
            # 解析返回结果
            content = response.choices[0].message.content
            try:
                result = json.loads(content)
                return float(result["score"]), result["reason"]
            except (json.JSONDecodeError, KeyError):
                # 如果返回结果不是有效的JSON，尝试从文本中提取分数
                import re
                score_match = re.search(r'(\d+(\.\d+)?)', content)
                if score_match:
                    return float(score_match.group(1)), content
                return 5.0, content
        
        except Exception as e:
            logger.error(f"计算标签匹配度时发生错误: {str(e)}", exc_info=True)
            return 5.0, "评分过程出错，默认给予中等评分"
    
    def generate_recommendation_report(self, task, top_rescuers):
        """
        生成推荐报告
        
        Args:
            task (dict): 任务信息
            top_rescuers (list): 排序后的前三名救援人员
        
        Returns:
            tuple: (HTML格式报告, Markdown格式报告)
        """
        try:
            # 构建提示词
            prompt = self._build_report_prompt(task, top_rescuers)
            
            # 调用智谱AI生成报告
            response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": "你是一个专业的救援报告生成系统，擅长整理数据并生成详细、专业的推荐报告。"},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.5,
                max_tokens=1500
            )
            
            # 获取报告内容
            markdown_report = response.choices[0].message.content
            
            # 生成HTML格式的报告
            html_report = self._generate_html_report(task, top_rescuers, markdown_report)
            
            return html_report, markdown_report
        
        except Exception as e:
            logger.error(f"生成报告时发生错误: {str(e)}", exc_info=True)
            return self._generate_fallback_report(task, top_rescuers)
    
    def _build_report_prompt(self, task, top_rescuers):
        """构建报告生成的提示词"""
        rescuers_info = []
        for i, rescuer in enumerate(top_rescuers):
            rescuers_info.append(f"""
            推荐人选 {i+1}：{rescuer['name']}
            - 距离走失地点约 {rescuer['distance']:.1f} 公里（距离得分：{rescuer['distance_score']:.1f}/10）
            - 技能标签：{', '.join(rescuer.get('skill_tags', []))}
            - 联系电话：{rescuer.get('phone', '未提供')}
            - 技能匹配度得分：{rescuer['tag_score']:.1f}/10
            - 匹配理由：{rescuer['tag_reason']}
            - 历史成功任务数：{rescuer['successful_tasks']} （经验得分：{rescuer['success_score']:.1f}/10）
            - 综合评分：{rescuer['total_score']:.1f}/10
            """)
        
        prompt = f"""
            请根据以下信息，生成一份走失老人救援人员推荐报告。

            **必须严格遵循以下要求：**
            1. 报告格式必须为 [GitHub Flavored Markdown]。
            2. 只输出 Markdown 主体（不要输出代码块包裹，不要输出 ```markdown）。
            3. 必须用 #、##、### 作为标题层级，列表用 - 或 1.。
            4. 重要数据用 **加粗**，不要用 HTML 标签。
            5. 每一部分之间空一行。
            6. 内容必须保证可直接被 Pandoc 解析成结构化 Word 样式（标题、列表、加粗等）。
            7. 绝对不要输出任何代码块（包括 ```markdown），只输出纯 markdown 报告内容！

            # 任务信息
            - 老人姓名：{task.get('elderName', '未知')}
            - 走失地点：{task.get('location', '未知')}
            - 额外信息：{task.get('extraInfo', '无')}

            # 推荐救援人员信息
            {"".join(rescuers_info)}

            请生成一份专业、详细、结构化的救援人员推荐报告，内容包括：
            1. 报告标题和任务信息
            2. 推荐救援人员详细信息和评分解释
            3. 推荐理由和建议行动

            **输出时不要包含任何代码块包裹，只输出 markdown 正文。**
            """
        return prompt
    
    def _generate_html_report(self, task, top_rescuers, markdown_report):
        """生成HTML格式的报告"""
        # 这里可以使用markdown2html库转换，但为简便起见，我们构建一个基本HTML
        html_rescuers = []
        for i, rescuer in enumerate(top_rescuers):
            html_rescuers.append(f"""
                <div class="rescuer-card">
                    <h4>推荐人选 {i+1}：<span class="rescuer-name">{rescuer['name']}</span></h4>
                    <ul>
                        <li>距离走失地点约 <strong>{rescuer['distance']:.1f} 公里</strong>（距离得分：{rescuer['distance_score']:.1f}/10）</li>
                        <li>技能标签：<strong>{', '.join(rescuer.get('skill_tags', []))}</strong></li>
                        <li>联系电话：<strong>{rescuer.get('phone', '未提供')}</strong></li>
                        <li>技能匹配度得分：<strong>{rescuer['tag_score']:.1f}/10</strong></li>
                        <li>匹配理由：{rescuer['tag_reason']}</li>
                        <li>历史成功任务数：<strong>{rescuer['successful_tasks']}</strong>（经验得分：{rescuer['success_score']:.1f}/10）</li>
                        <li>综合评分：<strong>{rescuer['total_score']:.1f}/10</strong></li>
                    </ul>
                </div>
                """)
        
        html_report = f"""
        <div class="recommendation-content">
            <h3>任务信息</h3>
            <p><strong>老人姓名：</strong>{task.get('elderName', '未知')}</p>
            <p><strong>走失地点：</strong>{task.get('location', '未知')}</p>
            <p><strong>额外信息：</strong>{task.get('extraInfo', '无')}</p>
            
            <h3>TOP 3 推荐救援人员</h3>
            {"".join(html_rescuers)}
            
            <p class="recommendation-note">本报告基于距离、技能匹配度和历史成功任务数三方面进行综合评估。<br>建议立即联系推荐人选安排救援行动。</p>
            
            <div class="markdown-content">
                <pre>{markdown_report}</pre>
            </div>
        </div>
        """
        
        return html_report
    
    def _generate_fallback_report(self, task, top_rescuers):
        """生成备用报告（当API调用失败时）"""
        markdown_report = f"""
        # 走失老人救援人员推荐报告
        
        ## 任务信息
        
        - **老人姓名**：{task.get('elderName', '未知')}
        - **走失地点**：{task.get('location', '未知')}
        - **额外信息**：{task.get('extraInfo', '无')}
        
        ## 推荐救援人员
        
        """
        
        for i, rescuer in enumerate(top_rescuers):
            markdown_report += f"""
        ### 推荐人选 {i+1}：{rescuer['name']}
        
        - 距离走失地点约 **{rescuer['distance']:.1f} 公里**（距离得分：{rescuer['distance_score']:.1f}/10）
        - 技能标签：**{', '.join(rescuer.get('skill_tags', []))}**
        - 技能匹配度得分：**{rescuer['tag_score']:.1f}/10**
        - 匹配理由：{rescuer['tag_reason']}
        - 历史成功任务数：**{rescuer['successful_tasks']}**（经验得分：{rescuer['success_score']:.1f}/10）
        - 综合评分：**{rescuer['total_score']:.1f}/10**
        
        """
        
        markdown_report += """
        ## 建议行动
        
        建议立即联系推荐人选安排救援行动。本报告基于距离、技能匹配度和历史成功任务数三方面进行综合评估。
        """
        
        # 生成简单的HTML报告
        html_report = f"""
        <div class="recommendation-content">
            <h3>任务信息</h3>
            <p><strong>老人姓名：</strong>{task.get('elderName', '未知')}</p>
            <p><strong>走失地点：</strong>{task.get('location', '未知')}</p>
            
            <h3>推荐救援人员</h3>
            <div class="markdown-content">
                <pre>{markdown_report}</pre>
            </div>
        </div>
        """
        
        return html_report, markdown_report