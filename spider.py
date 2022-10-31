# -*- coding: utf-8 -*-
# !/usr/bin/env python3
import json
import os
from datetime import datetime, timedelta
from time import time

from tabulate import tabulate
from bs4 import BeautifulSoup
from requests import Session


def extractDigit(s: str) -> int:
    try:
        return int("".join(list(filter(str.isdigit, s))))
    except:
        return 0


def getGMT8(format="%Y-%m-%d %H:%M", deltaMinuites=0) -> str:
    return (datetime.utcfromtimestamp((time())) + timedelta(hours=8, minutes=deltaMinuites)).strftime(format)


def checkAlive(session: Session):
    resp = session.get("https://api.issks.com/issksapi/V2/ec/chargingList.shtml")
    return "https://open.weixin.qq.com/connect/oauth2/authorize" not in resp.url


session = Session()
session.headers.update({
    "User-Agent": "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36 NetType/WIFI MicroMessenger/7.0.20.1781(0x6700143B) WindowsWechat(0x6307061d)",
    "Host": "api.issks.com"
})
session.cookies.update(dict(
    JSESSIONID=os.getenv("JSESSIONID"),
))

# 验证登录有效
if not checkAlive(session):
    print("Cookie失效")
    exit(-1)

stations = [
    {"iStationId": 117379, "vStationName": "第十餐厅1号机"},
    {"iStationId": 117392, "vStationName": "第十餐厅2号机"},
    {"iStationId": 117377, "vStationName": "游泳馆2号机"},
    {"iStationId": 117381, "vStationName": "游泳馆1号机"},
    # {"iStationId": 117369, "vStationName": "5栋2号机"},
    # {"iStationId": 117375, "vStationName": "5栋1号机"},
    # {"iStationId": 117387, "vStationName": "24栋1号机"},
    # {"iStationId": 117474, "vStationName": "24栋2号机"},
    # {"iStationId": 117372, "vStationName": "17栋2号机"},
    # {"iStationId": 117374, "vStationName": "17栋1号机"}
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
        isUsing, power, usedTime, totalTime = False, 0, 0, 0
        soup = BeautifulSoup(resp.text, "lxml")
        if soup.select_one(".state_item") is not None:
            isUsing = True
            power = extractDigit(soup.select_one(".state_item:nth-child(1) p").text)  # 瓦
            usedTime = extractDigit(soup.select_one(".state_item:nth-child(2) p").text)  # 分钟
            totalTime = extractDigit(soup.select(".state_item:nth-child(2) span")[2].text) * 30  # 分钟
            if totalTime == 0:
                totalTime = 999
        results.append(dict(
            vStationName=station["vStationName"],
            vOutletName=outlet["vOutletName"],
            isUsing=isUsing,
            power=power,
            usedTime=usedTime,
            totalTime=totalTime,
            remainingTime=totalTime - usedTime,
            usedAndTotalTimeDesc=f"{usedTime}/{totalTime}分钟",
            remainingTimeDesc=f"{(totalTime - usedTime) // 60}小时{(totalTime - usedTime) % 60}分",
            endTime=getGMT8("%Y-%m-%d %H:%M", totalTime - usedTime)
        ))

results = sorted(results, key=lambda x: x["remainingTime"])

tableHeader = ("充电桩", "插座号", "充电时长", "剩余时长", "结束时间")
tableBody = []
for result in results:
    if result["isUsing"]:
        tableBody.append([result["vStationName"],
                          result["vOutletName"],
                          result["usedAndTotalTimeDesc"],
                          result["remainingTimeDesc"],
                          result["endTime"]])
    else:
        tableBody.append([result["vStationName"],
                          result["vOutletName"],
                          "", "", ""])
html = """
<style>
table, th, td {
  border: 1px solid;
}
</style>
""" + f"""
<h3>更新时间：{getGMT8()}</h3>
{tabulate(tableBody, tableHeader, tablefmt="html")}
"""

open("public/index.html", mode="w", encoding="utf-8").write(html)
