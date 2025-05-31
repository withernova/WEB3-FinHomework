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
    
    def generate_recommendations(self, task, rescuers, tag_library=None):
        """
        生成救援人员推荐
        
        Args:
            task (dict): 任务信息
            rescuers (list): 救援人员列表
            tag_library (list): 标签库
        
        Returns:
            dict: 推荐结果
        """
        try:
            # 检查必要字段
            if not task.get("location") or not task.get("id") or not task.get("elderName"):
                return {"success": False, "message": "任务信息不完整"}
            
            if not rescuers or len(rescuers) == 0:
                return {"success": False, "message": "没有可用的救援人员"}
            
            # 从任务描述中提取相关标签
            extra_info = task.get("extraInfo", "")
            relevant_tags = []
            tag_importance = {}
            
            if extra_info and tag_library:
            # 使用大模型从额外信息中提取相关标签及其重要性
                relevant_tags, tag_importance = self.extract_relevant_tags(extra_info, tag_library)
                logger.info(f"从任务信息中提取的相关标签: {relevant_tags}")
                logger.info(f"标签重要性: {json.dumps(tag_importance, ensure_ascii=False, indent=2)}")
            
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
                tag_score, tag_match_details = self.calculate_tag_match_improved(
                    skill_tags, 
                    relevant_tags, 
                    tag_importance
                )
                
                # 计算历史成功任务分数
                successful_tasks = rescuer.get("successfulTasks", 0)
                
                # 历史任务分数 - 每个成功任务2分，上限10分
                success_score = min(10, successful_tasks * 2)
                
                # 计算总分 - 加权平均
                # 距离占40%，标签匹配占40%，历史任务占20%
                total_score = distance_score * 0.4 + tag_score * 0.4 + success_score * 0.2
                
                # 添加到结果列表
                scored_rescuers.append({
                    "uuid": rescuer["uuid"],
                    "name": rescuer["name"],
                    "phone": rescuer.get("phone", "未提供"),
                    "distance": distance,
                    "distance_score": distance_score,
                    "tag_score": tag_score,
                    "tag_match_details": tag_match_details,
                    "relevant_tags": relevant_tags,
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
            report_html, report_markdown = self.generate_recommendation_report(task, top_rescuers, relevant_tags)
            
            return {
                "success": True,
                "top_rescuers": top_rescuers,
                "report_html": report_html,
                "report_markdown": report_markdown,
                "report_text": report_markdown,
                "relevant_tags": relevant_tags
            }
        
        except Exception as e:
            logger.error(f"生成推荐时发生错误: {str(e)}", exc_info=True)
            return {"success": False, "message": f"生成推荐时发生错误: {str(e)}"}

    def extract_relevant_tags(self, extra_info, tag_library):
        """
        从额外信息中提取相关标签及其重要性
        
        Args:
            extra_info (str): 任务额外信息
            tag_library (list): 标签库
        
        Returns:
            tuple: (相关标签列表, 标签重要性字典)
        """
        try:
            logger.info("进入extract_relevant_tags方法")
            
            if not extra_info or not tag_library:
                logger.warning("额外信息或标签库为空，返回空结果")
                return [], {}
            
            # 简单匹配方法 (作为备选)
            simple_tags = []
            for tag in tag_library:
                if tag.lower() in extra_info.lower():
                    simple_tags.append(tag)
            
            simple_importance = {tag: {"importance": 3, "reason": "直接匹配"} for tag in simple_tags}
            logger.info(f"简单匹配的标签: {simple_tags}")
            
            # 如果没有找到任何标签，但仍希望使用一些标签进行测试
            if not simple_tags and tag_library:
                # 从标签库中随机选择最多5个标签
                import random
                sample_size = min(5, len(tag_library))
                sample_tags = random.sample(tag_library, sample_size)
                simple_tags = sample_tags
                simple_importance = {tag: {"importance": 3, "reason": "随机选择"} for tag in sample_tags}
                logger.info(f"随机选择的标签: {simple_tags}")
            
            # 构建提示词 - 限制只提取最多5个最相关的标签
            prompt = f"""
            任务描述：
            {extra_info}
            
            标签库：
            {', '.join(tag_library)}
            
            请根据任务描述推测老人可能遭遇了什么，依据此信息从标签库中提取最多8个与走失老人救援最相关的标签，并为每个相关标签分配重要性等级(1-5，5为最重要)。
            
            请使用JSON格式返回结果：
            {{
                "relevant_tags": [
                    {{"tag": "标签1", "importance": 5, "reason": "原因简述"}},
                    {{"tag": "标签2", "importance": 3, "reason": "原因简述"}}
                ]
            }}
            
            注意：
            1. 只选择确实相关的标签，不要强行匹配所有标签
            2. 不要在返回内容中添加任何代码块标记
            3. 只返回纯JSON内容，确保JSON格式有效且完整
            4. 最多只返回5个最相关的标签
            """
            
            logger.info("调用智谱AI模型提取标签")
            # 调用智谱AI模型
            try:
                response = self.client.chat.completions.create(
                    model=self.model,
                    messages=[
                        {"role": "system", "content": "你是一个专业的老人走失网站情形分析标签提取系统。请只返回纯JSON格式数据，不要添加任何代码块标记。返回的标签必须来自提供的标签库，且必须根据实际情况思考哪些标签可能会对本任务有帮助，例如老人在森林走失可能需要森林相关地形的技能，老人喜欢钓鱼，可能需要水域搜索等技能，务必因地制宜。最多返回10个最相关的标签。"},
                        {"role": "user", "content": prompt}
                    ],
                    temperature=0.3,
                    max_tokens=500  # 减少最大token数以避免生成太长的响应
                )
                
                # 获取返回内容
                content = response.choices[0].message.content
                logger.info(f"智谱AI返回内容: {content}")
                
                # 清理内容
                cleaned_content = self._clean_json_content(content)
                logger.info(f"清理后的内容: {cleaned_content}")
                
                # 解析JSON
                try:
                    result = json.loads(cleaned_content)
                    logger.info(f"解析JSON成功: {result}")
                    
                    relevant_tag_objects = result.get("relevant_tags", [])
                    logger.info(f"相关标签对象: {relevant_tag_objects}")
                    
                    # 提取标签和重要性
                    relevant_tags = []
                    tag_importance = {}
                    
                    for tag_obj in relevant_tag_objects:
                        tag = tag_obj.get("tag")
                        importance = tag_obj.get("importance", 3)
                        
                        logger.info(f"检查标签: {tag}, 是否在标签库中: {tag in tag_library}")
                        
                        if tag and tag in tag_library:
                            relevant_tags.append(tag)
                            tag_importance[tag] = {
                                "importance": importance,
                                "reason": tag_obj.get("reason", "")
                            }
                    
                    logger.info(f"智谱AI提取的相关标签: {relevant_tags}")
                    logger.info(f"标签重要性: {tag_importance}")
                    
                    # 如果没有找到标签，使用简单匹配结果
                    if not relevant_tags:
                        logger.warning("智谱AI未提取到有效标签，使用简单匹配结果")
                        return simple_tags, simple_importance
                    
                    return relevant_tags, tag_importance
                    
                except json.JSONDecodeError as e:
                    logger.error(f"解析JSON失败: {str(e)}")
                    # 尝试修复JSON
                    try:
                        import re
                        # 查找 relevant_tags 数组的开始和结束
                        match = re.search(r'"relevant_tags"\s*:\s*\[(.*?)\]', cleaned_content, re.DOTALL)
                        if match:
                            tags_content = match.group(1)
                            # 提取各个标签对象
                            tag_matches = re.finditer(r'\{\s*"tag"\s*:\s*"([^"]+)"\s*,\s*"importance"\s*:\s*(\d+)', tags_content)
                            
                            relevant_tags = []
                            tag_importance = {}
                            
                            for tag_match in tag_matches:
                                tag = tag_match.group(1)
                                importance = int(tag_match.group(2))
                                
                                if tag in tag_library:
                                    relevant_tags.append(tag)
                                    tag_importance[tag] = {
                                        "importance": importance,
                                        "reason": "从不完整JSON提取"
                                    }
                            
                            logger.info(f"从不完整JSON提取的标签: {relevant_tags}")
                            
                            if relevant_tags:
                                return relevant_tags, tag_importance
                    except Exception as e2:
                        logger.error(f"尝试修复JSON失败: {str(e2)}")
                    
                    # 如果所有方法都失败，返回简单匹配结果
                    return simple_tags, simple_importance
                    
            except Exception as e:
                logger.error(f"调用智谱AI失败: {str(e)}")
                return simple_tags, simple_importance
                
        except Exception as e:
            logger.error(f"提取相关标签时发生错误: {str(e)}", exc_info=True)
            return [], {}

    def _clean_json_content(self, content):
        """清理JSON内容，移除Markdown代码块标记和其他非JSON内容"""
        # 移除开头的```json或```
        if content.startswith("```"):
            if content.startswith("```json"):
                content = content[7:]
            else:
                content = content[3:]
        
        # 移除结尾的```
        if content.endswith("```"):
            content = content[:-3]
        
        # 去除前后空白
        content = content.strip()
        
        # 尝试找到JSON的开始和结束
        import re
        json_match = re.search(r'(\{.*\})', content, re.DOTALL)
        if json_match:
            content = json_match.group(1)
        
        return content

    def calculate_tag_match_improved(self, rescuer_tags, relevant_tags, tag_importance):
        """
        计算改进版的技能标签匹配度
        
        Args:
            rescuer_tags (list): 救援人员的技能标签
            relevant_tags (list): 任务相关的标签
            tag_importance (dict): 标签重要性
        
        Returns:
            tuple: (匹配分数, 匹配详情)
        """
        if not rescuer_tags or not relevant_tags:
            return 5.0, {"matched": [], "missing": relevant_tags, "reason": "无技能标签或无任务相关标签，给予中等评分"}
        
        # 计算匹配的标签和缺失的标签
        matched_tags = []
        missing_tags = []
        
        for tag in relevant_tags:
            if tag in rescuer_tags:
                matched_tags.append(tag)
            else:
                missing_tags.append(tag)
        
        # 计算匹配分数
        total_importance = sum(tag_importance.get(tag, {}).get("importance", 3) for tag in relevant_tags)
        matched_importance = sum(tag_importance.get(tag, {}).get("importance", 3) for tag in matched_tags)
        
        if total_importance == 0:
            match_score = 5.0  # 默认中等分数
        else:
            # 基于匹配的重要性计算分数
            match_ratio = matched_importance / total_importance
            match_score = match_ratio * 10
        
        # 生成匹配详情
        match_details = {
            "matched": matched_tags,
            "missing": missing_tags,
            "match_ratio": f"{matched_importance}/{total_importance}",
            "reason": self._generate_match_reason(matched_tags, missing_tags, tag_importance)
        }
        
        return match_score, match_details

    def _generate_match_reason(self, matched_tags, missing_tags, tag_importance):
        """生成标签匹配的原因说明"""
        if not matched_tags and not missing_tags:
            return "无需匹配的相关标签"
        
        reason = []
        
        if matched_tags:
            matched_str = ", ".join([f"{tag}(重要性:{tag_importance.get(tag, {}).get('importance', 3)})" for tag in matched_tags])
            reason.append(f"匹配标签: {matched_str}")
        
        if missing_tags:
            missing_str = ", ".join([f"{tag}(重要性:{tag_importance.get(tag, {}).get('importance', 3)})" for tag in missing_tags])
            reason.append(f"缺失标签: {missing_str}")
        
        return "；".join(reason)
    
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
    
    def generate_recommendation_report(self, task, top_rescuers, relevant_tags=None):
        """
        生成推荐报告
        
        Args:
            task (dict): 任务信息
            top_rescuers (list): 排序后的前三名救援人员
            relevant_tags (list): 任务相关的标签
        
        Returns:
            tuple: (HTML格式报告, Markdown格式报告)
        """
        try:
            # 构建提示词
            prompt = self._build_report_prompt(task, top_rescuers, relevant_tags)
            
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
            html_report = self._generate_html_report(task, top_rescuers, relevant_tags, markdown_report)
            
            return html_report, markdown_report
        
        except Exception as e:
            logger.error(f"生成报告时发生错误: {str(e)}", exc_info=True)
            return self._generate_fallback_report(task, top_rescuers, relevant_tags)
    
    def _build_report_prompt(self, task, top_rescuers, relevant_tags=None):
        """构建报告生成的提示词"""
        rescuers_info = []
        for i, rescuer in enumerate(top_rescuers):
            # 获取标签匹配详情
            tag_match_details = rescuer.get("tag_match_details", {})
            matched_tags = tag_match_details.get("matched", [])
            missing_tags = tag_match_details.get("missing", [])
            
            match_info = ""
            if matched_tags:
                match_info += f"匹配的关键标签: {', '.join(matched_tags)}\n        "
            if missing_tags:
                match_info += f"缺失的关键标签: {', '.join(missing_tags)}"
            
            rescuers_info.append(f"""
            推荐人选 {i+1}：{rescuer['name']}
            - 距离走失地点约 {rescuer['distance']:.1f} 公里（距离得分：{rescuer['distance_score']:.1f}/10）
            - 技能标签：{', '.join(rescuer.get('skill_tags', []))}
            - 联系电话：{rescuer.get('phone', '未提供')}
            - 技能匹配度得分：{rescuer['tag_score']:.1f}/10
            - {match_info}
            - 历史成功任务数：{rescuer['successful_tasks']} （经验得分：{rescuer['success_score']:.1f}/10）
            - 综合评分：{rescuer['total_score']:.1f}/10
            """)
        
        # 添加相关标签信息
        task_tags_info = ""
        if relevant_tags:
            task_tags_info = f"任务关键标签：{', '.join(relevant_tags)}\n    "
        
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
            - {task_tags_info}

            # 推荐救援人员信息
            {"".join(rescuers_info)}

            请生成一份专业、详细、结构化的救援人员推荐报告，内容包括：
            1. 报告标题和任务信息
            2. 推荐救援人员详细信息和评分解释
            3. 推荐理由和建议行动

            **输出时不要包含任何代码块包裹，只输出 markdown 正文。**
            """
        return prompt
        
    def _generate_html_report(self, task, top_rescuers, relevant_tags=None, markdown_report=""):
        """生成HTML格式的报告"""
        # 添加任务关键标签
        task_tag_html = ""
        if relevant_tags:
            task_tag_html = f"<p><strong>任务关键标签：</strong>{', '.join(relevant_tags)}</p>"
        
        html_rescuers = []
        for i, rescuer in enumerate(top_rescuers):
            # 获取标签匹配详情
            tag_match_details = rescuer.get("tag_match_details", {})
            matched_tags = tag_match_details.get("matched", [])
            missing_tags = tag_match_details.get("missing", [])
            
            match_info = ""
            if matched_tags:
                match_info += f"<li>匹配的关键标签: <strong>{', '.join(matched_tags)}</strong></li>"
            if missing_tags:
                match_info += f"<li>缺失的关键标签: {', '.join(missing_tags)}</li>"
            
            html_rescuers.append(f"""
                <div class="rescuer-card">
                    <h4>推荐人选 {i+1}：<span class="rescuer-name">{rescuer['name']}</span></h4>
                    <ul>
                        <li>距离走失地点约 <strong>{rescuer['distance']:.1f} 公里</strong>（距离得分：{rescuer['distance_score']:.1f}/10）</li>
                        <li>技能标签：<strong>{', '.join(rescuer.get('skill_tags', []))}</strong></li>
                        <li>联系电话：<strong>{rescuer.get('phone', '未提供')}</strong></li>
                        <li>技能匹配度得分：<strong>{rescuer['tag_score']:.1f}/10</strong></li>
                        {match_info}
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
            {task_tag_html}
            
            <h3>TOP 3 推荐救援人员</h3>
            {"".join(html_rescuers)}
            
            <p class="recommendation-note">本报告基于距离、技能标签匹配度和历史成功任务数三方面进行综合评估。<br>建议立即联系推荐人选安排救援行动。</p>
            
            <div class="markdown-content">
                <pre>{markdown_report}</pre>
            </div>
        </div>
        """
        
        return html_report
    
    def _generate_fallback_report(self, task, top_rescuers, relevant_tags=None):
        """生成备用报告（当API调用失败时）"""
        task_tags_info = ""
        if relevant_tags and len(relevant_tags) > 0:
            task_tags_info = f"- **任务关键标签**：{', '.join(relevant_tags)}\n"
        
        markdown_report = f"""
        # 走失老人救援人员推荐报告
        
        ## 任务信息
        
        - **老人姓名**：{task.get('elderName', '未知')}
        - **走失地点**：{task.get('location', '未知')}
        - **额外信息**：{task.get('extraInfo', '无')}
        {task_tags_info}
        
        ## 推荐救援人员
        
        """
        
        for i, rescuer in enumerate(top_rescuers):
            # 获取标签匹配详情
            tag_match_details = rescuer.get("tag_match_details", {})
            matched_tags = tag_match_details.get("matched", [])
            missing_tags = tag_match_details.get("missing", [])
            
            match_info = ""
            if matched_tags:
                match_info += f"- 匹配的关键标签: **{', '.join(matched_tags)}**\n"
            if missing_tags:
                match_info += f"- 缺失的关键标签: {', '.join(missing_tags)}\n"
            
            markdown_report += f"""
        ### 推荐人选 {i+1}：{rescuer['name']}
        
        - 距离走失地点约 **{rescuer['distance']:.1f} 公里**（距离得分：{rescuer['distance_score']:.1f}/10）
        - 技能标签：**{', '.join(rescuer.get('skill_tags', []))}**
        - 联系电话：**{rescuer.get('phone', '未提供')}**
        - 技能匹配度得分：**{rescuer['tag_score']:.1f}/10**
        {match_info}
        - 历史成功任务数：**{rescuer['successful_tasks']}**（经验得分：{rescuer['success_score']:.1f}/10）
        - 综合评分：**{rescuer['total_score']:.1f}/10**
        
        """
        
        markdown_report += """
        ## 建议行动
        
        建议立即联系推荐人选安排救援行动。本报告基于距离、技能匹配度和历史成功任务数三方面进行综合评估。
        """
        
        # 生成简单的HTML报告
        html_report = self._generate_html_report(task, top_rescuers, relevant_tags, markdown_report)
        
        return html_report, markdown_report
    


    def generate_elder_summary(self, template_data):
        """
        根据结构化模板数据生成走失老人的智能摘要
        
        Args:
            template_data (dict): 模板收集的详细信息
        
        Returns:
            str: 生成的摘要文本
        """
        try:
            logger.info("开始生成老人信息摘要")
            
            # 构建提示词
            prompt = self._build_summary_prompt(template_data)
            
            logger.info("调用智谱AI生成摘要")
            # 调用智谱AI模型
            response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": "你是一个专业的走失老人信息摘要生成系统，擅长将详细信息整理成简洁、重点突出的描述，便于救援人员快速理解关键信息。"},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=500
            )
            
            # 获取生成的摘要
            summary = response.choices[0].message.content
            logger.info(f"智谱AI返回摘要: {summary}")
            
            return summary.strip()
            
        except Exception as e:
            logger.error(f"生成摘要时发生错误: {str(e)}", exc_info=True)
            # 发生错误时使用本地方法生成摘要
            return self._generate_fallback_summary(template_data)

    def _build_summary_prompt(self, data):
        """构建摘要生成的提示词"""
        prompt = """
        请根据以下详细信息，生成一份简洁而全面的走失老人描述，重点突出对救援有帮助的关键信息：
        
        """
        
        # 基本信息
        basic_info = []
        if data.get('age'):
            basic_info.append(f"年龄：{data['age']}岁")
        if data.get('gender'):
            basic_info.append(f"性别：{data['gender']}")
        if data.get('height') or data.get('weight'):
            height_weight = "身高体重："
            if data.get('height'):
                height_weight += f"{data['height']}cm"
            if data.get('weight'):
                if data.get('height'):
                    height_weight += f"、{data['weight']}kg"
                else:
                    height_weight += f"{data['weight']}kg"
            basic_info.append(height_weight)
        
        if basic_info:
            prompt += "【基本信息】\n" + "\n".join(basic_info) + "\n\n"
        
        # 身体状况
        health_info = []
        if data.get('health'):
            health_info.append(f"健康状况：{', '.join(data['health'])}")
        if data.get('mobility'):
            health_info.append(f"行动能力：{', '.join(data['mobility'])}")
        if data.get('medication'):
            health_info.append(f"用药情况：{data['medication']}")
        
        if health_info:
            prompt += "【身体状况】\n" + "\n".join(health_info) + "\n\n"
        
        # 外貌特征
        appearance_info = []
        if data.get('bodyType'):
            appearance_info.append(f"体型：{', '.join(data['bodyType'])}")
        if data.get('hair'):
            appearance_info.append(f"发型：{', '.join(data['hair'])}")
        if data.get('marks'):
            appearance_info.append(f"特殊标记：{', '.join(data['marks'])}")
        
        clothing_info = []
        if data.get('topClothing'):
            clothing_info.append(f"上衣：{data['topClothing']}")
        if data.get('bottomClothing'):
            clothing_info.append(f"下装：{data['bottomClothing']}")
        if data.get('shoes'):
            clothing_info.append(f"鞋子：{data['shoes']}")
        
        if clothing_info:
            appearance_info.append("着装：" + "，".join(clothing_info))
        
        if data.get('belongings'):
            appearance_info.append(f"随身物品：{', '.join(data['belongings'])}")
        
        if appearance_info:
            prompt += "【外貌特征】\n" + "\n".join(appearance_info) + "\n\n"
        
        # 爱好与习惯
        hobby_info = []
        if data.get('hobbies'):
            hobby_info.append(f"爱好：{', '.join(data['hobbies'])}")
        if data.get('activities'):
            hobby_info.append(f"日常活动：{', '.join(data['activities'])}")
        if data.get('habits'):
            hobby_info.append(f"生活习惯：{data['habits']}")
        
        if hobby_info:
            prompt += "【爱好与习惯】\n" + "\n".join(hobby_info) + "\n\n"
        
        # 行为特点
        behavior_info = []
        if data.get('behavior'):
            behavior_info.append(f"行为特点：{', '.join(data['behavior'])}")
        if data.get('language'):
            behavior_info.append(f"语言能力：{', '.join(data['language'])}")
        if data.get('frequentPlaces'):
            behavior_info.append(f"常去地点：{', '.join(data['frequentPlaces'])}")
        
        if behavior_info:
            prompt += "【行为特点】\n" + "\n".join(behavior_info) + "\n\n"
        
        # 紧急情况
        if data.get('emergency'):
            prompt += f"【紧急注意事项】\n{', '.join(data['emergency'])}\n\n"
        
        # 其他信息
        if data.get('additionalInfo'):
            prompt += f"【其他信息】\n{data['additionalInfo']}\n\n"
        
        prompt += """
        请基于以上信息，生成一份结构清晰、重点突出的走失老人描述摘要，摘要应该：
        1. 包含老人最显著的特征，便于识别
        2. 突出需要特别注意的健康问题和紧急情况
        3. 提供可能的活动范围和习惯
        4. 语言简洁明了，避免冗余
        5. 总长度控制在200-300字以内
        
        直接返回摘要内容，不要加额外的标题或解释。
        """
        
        return prompt

    def _generate_fallback_summary(self, data):
        """生成备用摘要（当AI调用失败时）"""
        try:
            logger.info("使用本地方法生成备用摘要")
            
            summary_parts = []
            
            # 基本信息
            basic_info = []
            if data.get('age'):
                basic_info.append(f"{data['age']}岁")
            if data.get('gender'):
                basic_info.append(data['gender'])
            if data.get('height'):
                basic_info.append(f"身高约{data['height']}cm")
            if data.get('weight'):
                basic_info.append(f"体重约{data['weight']}kg")
            
            if basic_info:
                summary_parts.append("【基本特征】" + "、".join(basic_info))
            
            # 身体状况
            health_info = []
            if data.get('health'):
                health_info.append(f"健康：{', '.join(data['health'])}")
            if data.get('mobility'):
                health_info.append(f"行动：{', '.join(data['mobility'])}")
            if data.get('medication'):
                health_info.append(f"用药：{data['medication']}")
            
            if health_info:
                summary_parts.append("【身体状况】" + "；".join(health_info))
            
            # 外貌和着装
            appearance_info = []
            if data.get('bodyType'):
                appearance_info.append(f"体型：{', '.join(data['bodyType'])}")
            if data.get('hair'):
                appearance_info.append(f"发型：{', '.join(data['hair'])}")
            if data.get('marks'):
                appearance_info.append(f"特征：{', '.join(data['marks'])}")
            
            clothing = []
            if data.get('topClothing'):
                clothing.append(data['topClothing'])
            if data.get('bottomClothing'):
                clothing.append(data['bottomClothing'])
            if clothing:
                appearance_info.append(f"着装：{', '.join(clothing)}")
            
            if data.get('belongings'):
                appearance_info.append(f"随身物品：{', '.join(data['belongings'])}")
            
            if appearance_info:
                summary_parts.append("【外貌特征】" + "；".join(appearance_info))
            
            # 爱好与习惯
            hobby_info = []
            if data.get('hobbies'):
                hobby_info.append(f"爱好：{', '.join(data['hobbies'])}")
            if data.get('activities'):
                hobby_info.append(f"活动：{', '.join(data['activities'])}")
            
            if hobby_info:
                summary_parts.append("【爱好习惯】" + "；".join(hobby_info))
            
            # 行为特点
            behavior_info = []
            if data.get('behavior'):
                behavior_info.append(f"特点：{', '.join(data['behavior'])}")
            if data.get('frequentPlaces'):
                behavior_info.append(f"常去：{', '.join(data['frequentPlaces'])}")
            
            if behavior_info:
                summary_parts.append("【行为特点】" + "；".join(behavior_info))
            
            # 紧急情况
            if data.get('emergency'):
                summary_parts.append(f"【紧急注意】{', '.join(data['emergency'])}")
            
            # 其他信息
            if data.get('additionalInfo'):
                summary_parts.append(f"【其他信息】{data['additionalInfo']}")
            
            return "\n\n".join(summary_parts) if summary_parts else "详细信息已收集，但无法自动生成摘要。请查看完整信息。"
        
        except Exception as e:
            logger.error(f"生成备用摘要时发生错误: {str(e)}", exc_info=True)
            return "无法生成老人信息摘要，请手动填写详细信息。"