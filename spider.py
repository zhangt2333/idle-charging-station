#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
from dataclasses import dataclass
from enum import Enum
from collections import OrderedDict

from tabulate import tabulate
from bs4 import BeautifulSoup
from requests import Session

MAX_MINUTES = 999


@dataclass
class ChargingStation:
    class Status(Enum):
        USING = "使用中"
        AVAILABLE = "空闲中"
        UNAVAILABLE = "维护中"

    stationName: str
    outletName: str
    area: str
    status: Status = Status.AVAILABLE
    power: int = 0
    usedMinutes: int = 0
    totalMinutes: int = 0

    @property
    def remainingMinutes(self) -> int:
        return (self.totalMinutes - self.usedMinutes
                if self.status == ChargingStation.Status.USING else MAX_MINUTES)

    @property
    def usedAndTotalMinutesDesc(self) -> str:
        return (f"{self.usedMinutes}/{self.totalMinutes}分钟"
                if self.status == ChargingStation.Status.USING else "")

    @property
    def remainingTimeDesc(self) -> str:
        return (f"{self.remainingMinutes // 60}小时{self.remainingMinutes % 60}分钟"
                if self.status == ChargingStation.Status.USING else "")

    @property
    def endTimeDesc(self) -> str:
        return (getDatetime("%Y-%m-%d %H:%M", +8, self.remainingMinutes)
                if self.status == ChargingStation.Status.USING else "")

    @property
    def note(self) -> str:
        return (self.status.value
                if self.status == ChargingStation.Status.UNAVAILABLE else "")


def extractDigit(s: str) -> int:
    try:
        return int("".join(list(filter(str.isdigit, s))))
    except:
        return 0


def getDatetime(fmt="%Y-%m-%d %H:%M:%S", timezone=8, deltaMinutes=0):
    import datetime, time
    return ((datetime.datetime.utcfromtimestamp(time.time())
             + datetime.timedelta(hours=timezone, minutes=deltaMinutes))
            .strftime(fmt))


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

results: list[ChargingStation] = []

stations = [
    {"iStationId": 158507, "vStationName": "27栋1号机", "area": "四组团-27栋"},
    {"iStationId": 158748, "vStationName": "27栋2号机", "area": "四组团-27栋"},
    {"iStationId": 158501, "vStationName": "27栋3号机", "area": "四组团-27栋"},
    {"iStationId": 117387, "vStationName": "24栋1号机", "area": "四组团-24栋"},
    {"iStationId": 117474, "vStationName": "24栋2号机", "area": "四组团-24栋"},
    {"iStationId": 117372, "vStationName": "17栋2号机", "area": "四组团-17栋"},
    {"iStationId": 117374, "vStationName": "17栋1号机", "area": "四组团-17栋"},
    # {"iStationId": 117379, "vStationName": "第十餐厅1号机", "area": "三组团"},
    # {"iStationId": 117392, "vStationName": "第十餐厅2号机", "area": "三组团"},
    # {"iStationId": 117377, "vStationName": "游泳馆2号机", "area": "二组团"},
    # {"iStationId": 117381, "vStationName": "游泳馆1号机", "area": "二组团"},
    # {"iStationId": 117369, "vStationName": "5栋2号机", "area": "一组团"},
    # {"iStationId": 117375, "vStationName": "5栋1号机", "area": "一组团"},
]

for station in stations:
    stationId = station["iStationId"]
    resp = session.get("https://api.issks.com/issksapi/V2/ec/chargingList.shtml",
                       params=dict(stationId=stationId))
    assert resp.status_code == 200
    outlets = resp.json()["list"]
    for outlet in outlets:
        resp = session.get(f"https://api.issks.com/issksapi/V2/ec/charging/{outlet['vOutletNo']}.shtml")
        assert resp.status_code == 200
        chargingStation = ChargingStation(station["vStationName"], outlet["vOutletName"], station["area"])
        if "设备维护中" in resp.text:
            chargingStation.status = ChargingStation.Status.UNAVAILABLE
        elif (soup := BeautifulSoup(resp.text, "lxml")).select_one(".state_item"):
            chargingStation.power = extractDigit(soup.select_one(".state_item:nth-child(1) p").text)  # 瓦
            chargingStation.usedMinutes = extractDigit(soup.select_one(".state_item:nth-child(2) p").text)  # 分钟
            totalMinutes = extractDigit(soup.select(".state_item:nth-child(1) span")[-1].text) * 60  # 分钟
            chargingStation.totalMinutes = totalMinutes if totalMinutes != 0 else 600
        results.append(chargingStation)

results = sorted(results, key=lambda x: x.remainingMinutes)

html = """
<style>
table, th, td {
  border: 1px solid;
}
</style>
""" + f"""
<h1>更新时间：{getDatetime(fmt="%Y-%m-%d %H:%M")}</h1>
"""
htmlTableHeader = ("充电桩", "插座号", "充电时长", "剩余时长", "结束时间", "备注")

for area in list(OrderedDict.fromkeys([s["area"] for s in stations])):
    html += f"<h3>{area}</h3>"
    html += tabulate(list(map(lambda x: [x.stationName, x.outletName, x.usedAndTotalMinutesDesc,
                                         x.remainingTimeDesc, x.endTimeDesc, x.note],
                              filter(lambda x: x.area == area, results))),
                     htmlTableHeader, tablefmt="html")

if not os.path.exists("build"):
    os.mkdir("build")
open("build/index.html", mode="w", encoding="utf-8").write(html)
