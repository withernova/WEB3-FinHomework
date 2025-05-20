from zhipuai import ZhipuAI
from config import Config
import logging

logger = logging.getLogger(__name__)

class TagExtractor:
    """标签提取服务类"""
    
    def __init__(self, api_key=None, model=None):
        """初始化标签提取器

        Args:
            api_key (str, optional): 智谱API密钥. 默认使用配置文件中的密钥.
            model (str, optional): 模型名称. 默认使用配置文件中的模型.
        """
        self.api_key = api_key or Config.ZHIPU_API_KEY
        self.model = model or Config.MODEL_NAME
        self.client = ZhipuAI(api_key=self.api_key)
        logger.info(f"标签提取服务初始化完成，使用模型: {self.model}")
    
    def _get_system_prompt(self, tag_library):
        """根据标签库生成系统提示词

        Args:
            tag_library (list): 标签库列表

        Returns:
            str: 系统提示词
        """
        tags_str = "、".join(tag_library)
        return (
            f"你是一位志愿者任务匹配助手。我会提供志愿者个人简介，你必须仅从固定标签库中挑选最合适的标签，若个人简介不够清晰则可以不返回任何标签。"
            f"输出请直接给出一个中文逗号分隔的标签列表，必须保证和接下来的标签库中的标签完全一样，如果相似度不够则可以不选择，我们的标签匹配比较严格，勿附加多余解释。标签库：{tags_str}。"
        )
    
    def _chat(self, user_bio, tag_library):
        """调用大模型API进行对话

        Args:
            user_bio (str): 用户技能描述
            tag_library (list): 标签库列表

        Returns:
            str: 模型回复原始文本
        """
        system_prompt = self._get_system_prompt(tag_library)
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"个人简介：{user_bio}"}
        ]
        
        try:
            logger.debug(f"发送请求到智谱AI，个人简介: {user_bio[:100]}{'...' if len(user_bio) > 100 else ''}")
            response = self.client.chat.completions.create(
                model=self.model, 
                messages=messages
            )
            result = response.choices[0].message.content.strip()
            logger.debug(f"智谱AI响应: {result}")
            return result
        except Exception as e:
            logger.error(f"调用智谱AI时发生错误: {str(e)}")
            raise
    
    def extract_tags(self, user_bio, tag_library=None):
        """从用户简介中提取相关技能标签

        Args:
            user_bio (str): 用户技能描述
            tag_library (list, optional): 标签库. 默认使用配置文件中的标签库.

        Returns:
            list: 提取出的标签列表
        """
        if not user_bio or not user_bio.strip():
            logger.warning("用户描述为空")
            return ["描述为空，无法生成标签"]
        
        # 如果未提供标签库，使用默认标签库
        if tag_library is None:
            tag_library = Config.TAG_LIBRARY
        
        try:
            # 调用模型获取原始文本
            raw_response = self._chat(user_bio, tag_library)
            
            # 处理模型输出，允许中英文逗号
            candidates = [x.strip() for x in raw_response.replace("，", ",").split(",") if x.strip()]
            logger.info(f"模型提取的候选标签: {candidates}")
            
            # 与标签库取交集，确保标签有效
            valid_tags = [tag for tag in candidates if tag in tag_library]
            logger.info(f"有效标签: {valid_tags}")
            
            if not valid_tags:
                return ["未找到匹配标签，请优化个人简介"]
                
            return valid_tags
        except Exception as e:
            logger.error(f"提取标签时发生错误: {str(e)}")
            return ["标签提取过程出错，请重试"]