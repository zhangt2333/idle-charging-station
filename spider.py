# -*- coding: utf-8 -*-
# !/usr/bin/env python3
import json
import os
from datetime import datetime, timedelta
from time import time
from typing import List

from tabulate import tabulate
from bs4 import BeautifulSoup
from requests import Session


class ChargingStation:
    def __init__(self,
                 stationName: str,
                 outletName: str,
                 area: str,
                 isUsing: bool,
                 power: int,
                 usedMinutes: int,
                 totalMinutes: int):
        self.stationName = stationName
        self.outletName = outletName
        self.area = area
        self.isUsing = isUsing
        self.power = power
        self.usedMinutes = usedMinutes
        self.totalMinutes = totalMinutes
        self.remainingMinutes = totalMinutes - usedMinutes
        self.usedAndTotalMinutesDesc = ""
        self.remainingTimeDesc = ""
        self.endTimeDesc = ""
        if isUsing:
            self.usedAndTotalMinutesDesc = f"{usedMinutes}/{totalMinutes}分钟"
            self.remainingTimeDesc = f"{self.remainingMinutes // 60}小时{self.remainingMinutes % 60}分钟"
            self.endTimeDesc = getGMT8("%Y-%m-%d %H:%M", self.remainingMinutes)


def extractDigit(s: str) -> int:
    try:
        return int("".join(list(filter(str.isdigit, s))))
    except:
        return 0


def getGMT8(format="%Y-%m-%d %H:%M", deltaMinutes=0) -> str:
    return (datetime.utcfromtimestamp((time())) + timedelta(hours=8, minutes=deltaMinutes)).strftime(format)


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

results: List[ChargingStation] = []

for station in [
    {"iStationId": 117379, "vStationName": "第十餐厅1号机", "area": "三组团"},
    {"iStationId": 117392, "vStationName": "第十餐厅2号机", "area": "三组团"},
    {"iStationId": 117387, "vStationName": "24栋1号机", "area": "四组团"},
    {"iStationId": 117474, "vStationName": "24栋2号机", "area": "四组团"},
    {"iStationId": 117372, "vStationName": "17栋2号机", "area": "四组团"},
    {"iStationId": 117374, "vStationName": "17栋1号机", "area": "四组团"},
    {"iStationId": 117377, "vStationName": "游泳馆2号机", "area": "二组团"},
    {"iStationId": 117381, "vStationName": "游泳馆1号机", "area": "二组团"},
    # {"iStationId": 117369, "vStationName": "5栋2号机", "area": "一组团"},
    # {"iStationId": 117375, "vStationName": "5栋1号机", "area": "一组团"},
]:
    stationId = station["iStationId"]
    resp = session.get("https://api.issks.com/issksapi/V2/ec/chargingList.shtml",
                       params=dict(stationId=stationId))
    assert resp.status_code == 200
    outlets = json.loads(resp.text)["list"]
    for outlet in outlets:
        resp = session.get(f"https://api.issks.com/issksapi/V2/ec/charging/{outlet['vOutletNo']}.shtml")
        assert resp.status_code == 200

        isUsing, power, usedMinutes, totalMinutes = False, 0, 0, 0
        if (soup := BeautifulSoup(resp.text, "lxml")).select_one(".state_item"):
            isUsing = True
            power = extractDigit(soup.select_one(".state_item:nth-child(1) p").text)  # 瓦
            usedMinutes = extractDigit(soup.select_one(".state_item:nth-child(2) p").text)  # 分钟
            totalMinutes = extractDigit(soup.select(".state_item:nth-child(1) span")[-1].text) * 60  # 分钟
            totalMinutes = totalMinutes if totalMinutes != 0 else 600

        results.append(ChargingStation(station["vStationName"], outlet["vOutletName"], station["area"],
                                       isUsing, power, usedMinutes, totalMinutes))

results = sorted(results, key=lambda x: x.remainingMinutes)

html = """
<style>
table, th, td {
  border: 1px solid;
}
</style>
""" + f"""
<h2>更新时间：{getGMT8()}</h2>
"""
htmlTableHeader = ("充电桩", "插座号", "充电时长", "剩余时长", "结束时间")

areas = ["三组团", "四组团", "二组团", "一组团"]
for area in areas:
    html += f"<h4>{area}</h4>"
    html += tabulate(list(map(lambda x: [x.stationName, x.outletName, x.usedAndTotalMinutesDesc,
                                         x.remainingTimeDesc, x.endTimeDesc],
                              filter(lambda x: x.area == area, results))),
                     htmlTableHeader, tablefmt="html")

if not os.path.exists("build"):
    os.mkdir("build")
open("build/index.html", mode="w", encoding="utf-8").write(html)
