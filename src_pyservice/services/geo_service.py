# services/geo_service.py
import requests
from config import Config
import logging

logger = logging.getLogger(__name__)

class GeoService:
    def __init__(self, api_key=None):
        self.api_key = api_key or Config.MAP_API_KEY
    
    def get_coordinates(self, address):
        """
        获取地址的经纬度坐标
        
        Args:
            address (str): 地址字符串
        
        Returns:
            tuple: (纬度, 经度) 或 None
        """
        try:
            # 使用高德地图API获取坐标
            url = f"https://restapi.amap.com/v3/geocode/geo"
            params = {
                "address": address,
                "output": "json",
                "key": self.api_key
            }
            
            response = requests.get(url, params=params)
            data = response.json()
            
            if data.get('status') == '1' and data.get('geocodes') and len(data['geocodes']) > 0:
                location = data['geocodes'][0]['location'].split(',')
                # 返回 (纬度, 经度)
                return float(location[1]), float(location[0])
            
            logger.warning(f"无法获取地址坐标: {address}, 响应: {data}")
            return None
        
        except Exception as e:
            logger.error(f"获取坐标时发生错误: {str(e)}", exc_info=True)
            return None
    
    def calculate_distance(self, location1, location2):
        """
        计算两个地点之间的距离（公里）
        
        Args:
            location1 (str): 地点1的地址字符串
            location2 (str): 地点2的地址字符串
        
        Returns:
            float: 距离（公里）或 None
        """
        try:
            # 尝试直接使用高德地图的距离计算API
            url = f"https://restapi.amap.com/v3/distance"
            params = {
                "key": self.api_key,
                "origins": location1,
                "destination": location2,
                "type": 1  # 直线距离
            }
            
            response = requests.get(url, params=params)
            data = response.json()
            
            if data.get('status') == '1' and data.get('results') and len(data['results']) > 0:
                # 距离单位为米，转换为公里
                distance_meters = float(data['results'][0]['distance'])
                return distance_meters / 1000.0
            
            # 如果直接计算失败，尝试先获取坐标再计算
            coords1 = self.get_coordinates(location1)
            coords2 = self.get_coordinates(location2)
            
            if coords1 and coords2:
                # 使用简化的球面距离公式计算（可以替换为更精确的计算方法）
                from math import radians, sin, cos, sqrt, atan2
                
                lat1, lon1 = radians(coords1[0]), radians(coords1[1])
                lat2, lon2 = radians(coords2[0]), radians(coords2[1])
                
                # 半正矢公式
                dlon = lon2 - lon1
                dlat = lat2 - lat1
                a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
                c = 2 * atan2(sqrt(a), sqrt(1-a))
                
                # 地球平均半径（公里）
                radius = 6371.0
                distance = radius * c
                
                return distance
            
            logger.warning(f"无法计算距离: {location1} -> {location2}")
            return None
        
        except Exception as e:
            logger.error(f"计算距离时发生错误: {str(e)}", exc_info=True)
            return None