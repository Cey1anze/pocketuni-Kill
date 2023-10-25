# PU口袋校园活动定时报名

**由于本程序全程自动化且使用多线程，理论上非常暴力,由此导致的所有后果请自行承担**

*有投诉随时跑路*

## 准备工作

**下载Release中最新的PUSeckill.zip, 解压缩**

- 电脑端浏览器(Edge Chrome Firefox)
- JDK1.8+
- PU平台 **扫一扫** 或会复制cookies
- 会用 **记事本** 修改.properties配置文件

## 启动前配置

### 修改配置文件

***请完全理解并熟悉以下内容再进行快速启动！！！***

> **A , B方法任选其一，A方法更安全更简单，B方法最稳定（但需会复制cookies）**

#### A.使用PU平台扫一扫自动登录

**此方法无需获取cookies，更安全**

- 修改setting.properties配置文件
  
  activityID_1=（活动ID，获取方式见下）
  
  ifSchedule=（true 为定时抢活动，false为立即抢活动，推荐使用true，并定时到活动报名开始时间）
  
  mySchedule=（严格按照格式）
  
  useLocalCookies=false（关闭cookie登录）

#### B.使用cookies登录

- 浏览器登录完成->按键盘F12选择Console(控制台)->输入> document.cookie复制
  
  cookies**不包含引号**

- 修改setting.properties配置文件
  
  myCookies=（复制的cookies）
  
  activityID_1=（活动ID，获取方式见下）
  
  ifSchedule=（true 为定时抢活动，false为立即抢活动，推荐使用true，并定时到活动报名开始时间）
  
  mySchedule=（严格按照格式）
  
  useLocalCookies=true（开启cookie登录）

#### 关于如何获得活动编号

- 点入要参加的活动，电脑端查看地址栏内容，手机端分享活动到其他地方（QQ发送至电脑,微信发送至电脑），会生成分享连接
  
  > https://pc.pocketuni.net/active/detail?id=xxxxx

- 复制id值

- 修改setting.properties配置文件
  
  activityID_1=（活动id）

## 快速启动

配置好 **setting.properties** 文件后直接双击 **run.bat** 启动程序，程序启动后会在当前目录生成一个二维码图片 **`output.png`** ，并要求你进行二维码登陆，请使用手机版PU口袋校园扫一扫，扫码时间设定为 **20秒** ，请在20秒内 **扫描二维码并确认登陆** ！！手机确认后程序仍然会等待到时间结束，无需担心。登陆完成后程序会全程自动运行，无需任何人为干预，运行期间不要关闭运行窗口

（黑色二维码扫不上的请开启资源管理器文件预览功能，单击图片即可预览到白色的二维码）

![](https://cdn.jsdelivr.net/gh/Cey1anze/Blog_Images@main/pic/202310052210894.png)

![](https://cdn.jsdelivr.net/gh/Cey1anze/Blog_Images@main/pic/202310251232383.png)

### 提示

- 除上面提到的参数需要自行修改，其余所有参数请不要改动，否则程序无法运行

- 定时时间一定要严格按照示例格式输入，使用24小时制！！！
