# UV5R Mini CPS (Android BLE)

[English](#english)

一个面向 `Baofeng UV5R MINI` 手台的 Android 蓝牙写频工具（实验性）。

## 当前功能

- 蓝牙读取整机codeplug
- 信道表编辑（1-999）
- 新增/删除信道
- VFO 模式编辑
- 手台设置编辑
- DTMF 设置编辑
- 写回设备

## 安全提示

- 本项目是**实验性工具**，存在改写错误、参数错位、通信异常等风险
- 使用前请先做完整备份
- 不要在关键通信场景下直接使用未验证配置
- 因使用本工具导致的设备异常或数据损坏，使用者需自行承担风险

## 使用方法

1. 打开 App，输入手台 MAC
2. 点击“读取设备”
3. 在信道列表 / VFO / 手台设置 / DTMF 页面编辑参数
4. 点击“写回设备”
5. 等待写入完成与手台重启

## TODO
1. ~~蓝牙设备自动扫描及列表选择~~
2. 英文翻译

## 免责声明

本仓库仅用于技术研究与个人学习交流，请遵守你所在地区的无线电法规与设备使用规范。

---

## English

This is an experimental Android Bluetooth programming tool for the `Baofeng UV5R MINI` handheld radio.

## Current Features

- Read full device codeplug over Bluetooth
- Channel table editing (1-999)
- Add / delete channels
- VFO mode editing
- Radio settings editing
- DTMF settings editing
- Write back to device

## Safety Notes

- This project is **experimental** and may have risks such as incorrect writes, field mismatch, or communication failures.
- Always make a full backup before use.
- Do not use unverified configurations in mission-critical communication scenarios.
- You are responsible for any device issues or data loss caused by using this tool.

## Usage

1. Open the app and enter the radio MAC address.
2. Tap “Read Device”.
3. Edit parameters in Channel List / VFO / Radio Settings / DTMF pages.
4. Tap “Write Device”.
5. Wait for write completion and radio reboot.

## TODO
1. ~~BLE device scan and selection~~
2. English localization


## Disclaimer

This repository is for technical research and personal learning only. Please comply with radio regulations and equipment usage rules in your region.
