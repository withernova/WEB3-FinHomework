from flask import Flask
from api.routers import api_bp
from utils.logging_config import setup_logging
from config import Config
import logging

def create_app():
    """创建并配置Flask应用"""
    # 配置日志
    setup_logging(log_level=logging.INFO, log_file='logs/app.log')
    logger = logging.getLogger(__name__)
    
    # 创建Flask应用
    app = Flask(__name__)
    
    # 注册蓝图
    app.register_blueprint(api_bp)
    
    logger.info("应用初始化完成")
    return app

if __name__ == "__main__":
    # 创建应用
    app = create_app()
    
    # 启动服务器
    app.run(
        host=Config.HOST, 
        port=Config.PORT, 
        debug=Config.DEBUG
    )