import os
import subprocess
import logging
import tempfile
from config import Config

logger = logging.getLogger(__name__)

import re

def extract_markdown_content(text):
    """
    提取以三引号包裹的 markdown 内容块
    支持 '''markdown ... ''' 或 ```markdown ... ```
    """
    # 支持三种写法：'''markdown、```markdown、''' 和 ```
    pattern = r"(?:'''|```)markdown\s*(.*?)(?:'''|```)$"
    match = re.search(pattern, text, re.DOTALL | re.IGNORECASE)
    if match:
        return match.group(1).strip()
    return text.strip()  # 如果没有包裹直接返回原文

def generate_docx_from_markdown(
    markdown_input, output_path, template_path=None, is_text=False
):
    """
    用pandoc将Markdown文本或文件转换为Word文档
    """
    try:
        if is_text:
            md = extract_markdown_content(markdown_input)
            with tempfile.NamedTemporaryFile('w', delete=False, suffix='.md', encoding='utf-8') as f:
                f.write(md)
                markdown_path = f.name
        else:
            markdown_path = markdown_input

        cmd = ["pandoc", markdown_path, "-o", output_path]
        if template_path and os.path.exists(template_path):
            cmd.append(f"--reference-doc={template_path}")

        process = subprocess.run(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            check=True, text=True
        )

        if process.returncode != 0:
            logger.error(f"Pandoc转换错误: {process.stderr}")
            raise Exception(f"Pandoc转换错误: {process.stderr}")

        if not os.path.exists(output_path):
            raise Exception("Word文档生成失败")

        return output_path

    except subprocess.CalledProcessError as e:
        logger.error(f"Pandoc执行失败: {e.stderr}", exc_info=True)
        raise Exception(f"Pandoc执行失败: {e.stderr}")

    except Exception as e:
        logger.error(f"生成Word文档时发生错误: {str(e)}", exc_info=True)
        raise

    finally:
        if is_text and 'markdown_path' in locals():
            try:
                os.remove(markdown_path)
            except Exception:
                pass