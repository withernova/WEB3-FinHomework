import os

class Config:
    """全局配置"""

    # ---------- 智谱 ----------
    ZHIPU_API_KEY = os.environ.get('ZHIPUAI_API_KEY', '56206a5488ca043b2cbdc268186efe17.NM2JqxxMDgNbezB9')
    MODEL_NAME    = "glm-4-flash"

    # ---------- Flask ----------
    HOST  = "0.0.0.0"
    PORT  = 5000
    DEBUG = False

    # ---------- 数据库 ----------
    # 例如:  mysql+pymysql://user:pwd@127.0.0.1:3306/web
    DB_URL = os.getenv("VOLUNTEER_DB_URL",
                       "mysql+pymysql://root:114514@123.249.76.205/web")

    # ---------- 默认标签库 ----------
    TAG_LIBRARY = [
        "城市导航","水域导航","山区熟悉","丛林越野","医疗背景","心理辅导","语言翻译",
        "急救技能","野外急救","攀登技能","搜索技能","沟通能力","团队协作","任务分配",
        "高海拔耐受","野生动物识别","计算机技能","无人机操作","长时间徒步","高强度耐力",
        "急速奔跑","热带森林熟悉","沙漠求生","极限运动","高空作业","紧急反应","食物处理",
        "物资管理","水上救援","森林跟踪","灾后救援"
    ]

    # ---------- 推荐算法权重 ----------
    WEIGHT_DISTANCE = 0.4   # 距离权重（越近越高）
    WEIGHT_TAG      = 0.3   # 标签匹配权重
    WEIGHT_SUCCESS  = 0.3   # 历史成功数权重
