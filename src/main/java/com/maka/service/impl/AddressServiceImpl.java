package com.maka.service.impl;

import com.maka.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.json.JSONObject;

import java.util.List;

@Service
public class AddressServiceImpl implements AddressService {

    // 假设您有几个地址在这里
    private final Address[] addresses = {
        new Address("江苏省无锡市梁溪区南禅寺派出所", "2665482319@qq.com"),
        new Address("江苏省无锡市滨湖区雪浪派出所", "1193220118@stu.jiangnan.edu.cn")
    };

    @Override
    public String findClosestAddressEmail(double lostLongitude, double lostLatitude) {
        String closestEmail = null;
        double minDistance = Double.MAX_VALUE;

        for (Address address : addresses) {
            // 将具体地址转换为经纬度
            double[] latLng = getCoordinates(address.getLocation());
            double addressLongitude = latLng[0];
            double addressLatitude = latLng[1];

            double distance = calculateDistance(lostLongitude, lostLatitude, addressLongitude, addressLatitude);
            if (distance < minDistance) {
                minDistance = distance;
                closestEmail = address.getEmail();
            }
        }
        return closestEmail;
    }

    private double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
        final int R = 6371; // 地球半径，单位: 公里
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // 返回公里数
    }

    private double[] getCoordinates(String address) {
        // 使用高德地图 API 获取经纬度
        RestTemplate restTemplate = new RestTemplate();
        String URL = "https://restapi.amap.com/v3/geocode/geo?address=" + address + "&key=d688586468dd49b708f89590e83dd259";

        ResponseEntity<String> response = restTemplate.getForEntity(URL, String.class);
        String jsonResponse = response.getBody();

        JSONObject jsonObject = new JSONObject(jsonResponse);
        if (jsonObject.getJSONArray("geocodes").length() > 0) {
            String location = jsonObject.getJSONArray("geocodes").getJSONObject(0).getString("location");
            String[] latLng = location.split(",");
            return new double[]{Double.parseDouble(latLng[0]), Double.parseDouble(latLng[1])};
        }
        return new double[]{0.0, 0.0}; // 如果未找到，返回默认值
    }

    private static class Address {
        private String location;
        private String email;

        public Address(String location, String email) {
            this.location = location;
            this.email = email;
        }

        public String getLocation() {
            return location;
        }

        public String getEmail() {
            return email;
        }
    }
}
