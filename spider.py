# -*- coding: utf-8 -*-
# !/usr/bin/env python3
# Copyright 2022 zhangt2333. All Rights Reserved.
import json
from datetime import datetime, timedelta
from time import time

from bs4 import BeautifulSoup
from requests import Session


def extractDigit(s: str) -> int:
    try:
        return int("".join(list(filter(str.isdigit, s))))
    except Exception as e:
        return 0


def getGMT8(format: str, deltaMinuites=0) -> str:
    return (datetime.utcfromtimestamp((time())) + timedelta(hours=8, minutes=deltaMinuites)).strftime(format)


session = Session()
session.headers.update({
    "User-Agent": "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36 NetType/WIFI MicroMessenger/7.0.20.1781(0x6700143B) WindowsWechat(0x6307061d)",
    "Host": "api.issks.com"
})
session.cookies.update(dict(
    JSESSIONID="075CE8ED557766E80300AD56B1C88D2E",
))

# resp = session.get("https://api.issks.com/issksapi/V2/ec/stationList/json.shtml",
#                    params=dict(mapX=118.958406, mapY=32.119560))
# assert resp.status_code == 200
# stations = json.loads(resp.text)["list"]
# stations = list(filter(lambda s: "南京大学" in s["vStationName"], stations))
stations = [
    {"iStationId": 117379, "vStationName": "南京大学仙林校区-第十餐厅1号机"},
    {"iStationId": 117392, "vStationName": "南京大学仙林校区-第十餐厅2号机"},
    {"iStationId": 117377, "vStationName": "南京大学仙林校区-游泳馆2号机"},
    {"iStationId": 117381, "vStationName": "南京大学仙林校区-游泳馆1号机"},
    # {"iStationId": 117369, "vStationName": "南京大学仙林校区-5栋2号机"},
    # {"iStationId": 117375, "vStationName": "南京大学仙林校区-5栋1号机"},
    # {"iStationId": 117387, "vStationName": "南京大学仙林校区-24栋1号机"},
    # {"iStationId": 117474, "vStationName": "南京大学仙林校区-24栋2号机"},
    # {"iStationId": 117372, "vStationName": "南京大学仙林校区-17栋2号机"},
    # {"iStationId": 117374, "vStationName": "南京大学仙林校区-17栋1号机"}
]

results = []

for station in stations:
    stationId = station["iStationId"]
    resp = session.get("https://api.issks.com/issksapi/V2/ec/chargingList.shtml",
                       params=dict(stationId=stationId))
    assert resp.status_code == 200
    outlets = json.loads(resp.text)["list"]
    for outlet in outlets:
        resp = session.get(f"https://api.issks.com/issksapi/V2/ec/charging/{outlet['vOutletNo']}.shtml")
        assert resp.status_code == 200
        soup = BeautifulSoup(resp.text, "lxml")
        isUsing = soup.select_one(".state_item") is not None
        if isUsing:
            power = extractDigit(soup.select_one(".state_item:nth-child(1) p").text)  # 瓦
            usedTime = extractDigit(soup.select_one(".state_item:nth-child(2) p").text)  # 分钟
            totalTime = extractDigit(soup.select(".state_item:nth-child(2) span")[2].text) * 30  # 分钟
            if totalTime == 0:
                totalTime = 999
        else:
            power, usedTime, totalTime = 0, 0, 0
        results.append(dict(
            vStationName=station["vStationName"],
            vOutletName=outlet["vOutletName"],
            isUsing=isUsing,
            power=power,
            usedTime=usedTime,
            totalTime=totalTime,
            remainingTime=totalTime - usedTime,
            desc=f"充电{usedTime}/{totalTime}分钟, 还剩{totalTime - usedTime}分钟, 即{(totalTime - usedTime) // 60}小时{(totalTime - usedTime) % 60}分",
            endTime=getGMT8('%Y-%m-%d %H:%M', totalTime - usedTime)
        ))

for result in sorted(results, key=lambda x: x["remainingTime"]):
    if result["isUsing"]:
        print(result["vStationName"], result["vOutletName"], result["desc"], result["endTime"])
    else:
        print(result["vStationName"], result["vOutletName"])
