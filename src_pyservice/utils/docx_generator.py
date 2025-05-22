# utils/docx_generator.py
import os
import subprocess
import logging
from config import Config

logger = logging.getLogger(__name__)

def generate_docx_from_markdown(markdown_path, output_path, template_path=None):
    """
    使用pandoc将Markdown文件转换为Word文档
    
    Args:
        markdown_path (str): Markdown文件路径
        output_path (str): 输出Word文件路径
        template_path (str, optional): Word模板文件路径
    
    Returns:
        str: 生成的文档路径
    """
    try:
        cmd = ["pandoc", markdown_path, "-o", output_path]
        
        # 如果指定了模板，添加模板参数
        if template_path and os.path.exists(template_path):
            cmd.extend(["--reference-doc", template_path])
        
        # 执行pandoc命令
        process = subprocess.run(
            cmd, 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE,
            check=True,
            text=True
        )
        
        # 检查命令是否成功执行
        if process.returncode != 0:
            logger.error(f"Pandoc转换错误: {process.stderr}")
            raise Exception(f"Pandoc转换错误: {process.stderr}")
        
        # 确认文件已生成
        if not os.path.exists(output_path):
            raise Exception("Word文档生成失败")
        
        return output_path
    
    except subprocess.CalledProcessError as e:
        logger.error(f"Pandoc执行失败: {e.stderr}", exc_info=True)
        raise Exception(f"Pandoc执行失败: {e.stderr}")
    
    except Exception as e:
        logger.error(f"生成Word文档时发生错误: {str(e)}", exc_info=True)
        raise Exception(f"生成Word文档时发生错误: {str(e)}")