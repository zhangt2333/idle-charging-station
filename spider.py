#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
from dataclasses import dataclass
from enum import Enum
from collections import OrderedDict

import asyncio
import aiohttp
from tabulate import tabulate
from bs4 import BeautifulSoup
from aiohttp import ClientSession

MAX_MINUTES = 999

stations = [
    {"iStationId": 158789, "vStationName": "26栋1号机", "area": "四组团-26栋"},
    {"iStationId": 158742, "vStationName": "26栋2号机", "area": "四组团-26栋"},
    {"iStationId": 158664, "vStationName": "26栋3号机", "area": "四组团-26栋"},
    {"iStationId": 158507, "vStationName": "27栋1号机", "area": "四组团-27栋"},
    {"iStationId": 158748, "vStationName": "27栋2号机", "area": "四组团-27栋"},
    {"iStationId": 158501, "vStationName": "27栋3号机", "area": "四组团-27栋"},
    {"iStationId": 158764, "vStationName": "27栋4号机", "area": "四组团-27栋"},
    {"iStationId": 158666, "vStationName": "27栋5号机", "area": "四组团-27栋"},
    {"iStationId": 158661, "vStationName": "27栋6号机", "area": "四组团-27栋"},
    {"iStationId": 158668, "vStationName": "27栋7号机", "area": "四组团-27栋"},
    {"iStationId": 117387, "vStationName": "24栋1号机", "area": "四组团-24栋"},
    {"iStationId": 117474, "vStationName": "24栋2号机", "area": "四组团-24栋"},
    {"iStationId": 117374, "vStationName": "17栋（8插座）1号机", "area": "四组团-17栋"},
    {"iStationId": 117372, "vStationName": "17栋（8插座）2号机", "area": "四组团-17栋"},
    {"iStationId": 165088, "vStationName": "17栋（8插座）3号机", "area": "四组团-17栋"},
    {"iStationId": 158676, "vStationName": "17栋（10插座）1号机", "area": "四组团-17栋"},
    {"iStationId": 157805, "vStationName": "17栋（10插座）2号机", "area": "四组团-17栋"},
    {"iStationId": 157551, "vStationName": "17栋（10插座）3号机", "area": "四组团-17栋"},
    # {"iStationId": 117379, "vStationName": "第十餐厅1号机", "area": "三组团"},
    # {"iStationId": 117392, "vStationName": "第十餐厅2号机", "area": "三组团"},
    # {"iStationId": 117377, "vStationName": "游泳馆2号机", "area": "二组团"},
    # {"iStationId": 117381, "vStationName": "游泳馆1号机", "area": "二组团"},
    # {"iStationId": 117369, "vStationName": "5栋2号机", "area": "一组团"},
    # {"iStationId": 117375, "vStationName": "5栋1号机", "area": "一组团"},
]


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
                if self.status != ChargingStation.Status.UNAVAILABLE else MAX_MINUTES)

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


async def checkAlive(session: ClientSession) -> bool:
    async with session.get("https://api.issks.com/issksapi/V2/ec/userInfo.shtml") as resp:
        text = await resp.text()
        return text.strip() != ""


async def fetchOutletList(session: ClientSession, station: dict) -> list[dict]:
    async with session.get("https://api.issks.com/issksapi/V2/ec/chargingList.shtml",
                           params=dict(stationId=station["iStationId"])) as resp:
        assert resp.status == 200
        return (await resp.json())["list"]


async def fetchOutletDetail(session: ClientSession, station: dict, outlet: dict) -> ChargingStation:
    async with session.get(f"https://api.issks.com/issksapi/V2/ec/charging/{outlet['vOutletNo']}.shtml") as resp:
        assert resp.status == 200
        text = await resp.text()
        chargingStation = ChargingStation(station["vStationName"], outlet["vOutletName"], station["area"])
        if "设备维护中" in text or outlet["status"] == 3:  # status=3 时页面提示该插座安全隐患不可用
            chargingStation.status = ChargingStation.Status.UNAVAILABLE
        elif (soup := BeautifulSoup(text, "lxml")).select_one(".state_item"):
            chargingStation.status = ChargingStation.Status.USING
            chargingStation.power = extractDigit(soup.select_one(".state_item:nth-child(1) p").text)  # 瓦
            chargingStation.usedMinutes = extractDigit(soup.select_one(".state_item:nth-child(2) p").text)  # 分钟
            totalMinutes = extractDigit(soup.select(".state_item:nth-child(1) span")[-1].text) * 60  # 分钟
            chargingStation.totalMinutes = totalMinutes if totalMinutes != 0 else 600
        return chargingStation


async def main():
    session = aiohttp.ClientSession(trust_env=True)
    session.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36 NetType/WIFI MicroMessenger/7.0.20.1781(0x6700143B) WindowsWechat(0x6307061d)",
        "Host": "api.issks.com"
    })
    session.cookie_jar.update_cookies(dict(
        JSESSIONID=os.getenv("JSESSIONID"),
    ))

    # 验证登录有效
    if not await checkAlive(session):
        print("Cookie失效")
        exit(-1)

    tasks = []
    for station in stations:
        tasks.append(asyncio.ensure_future(fetchOutletList(session, station)))
    outletLists = await asyncio.gather(*tasks)

    tasks = []
    for station, outlets in zip(stations, outletLists):
        for outlet in outlets:
            tasks.append(asyncio.ensure_future(fetchOutletDetail(session, station, outlet)))
    results = await asyncio.gather(*tasks)

    await session.close()

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


asyncio.run(main())
