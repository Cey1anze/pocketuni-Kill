# PU口袋校园活动定时报名

**由于本程序全程自动化且使用多线程，理论上非常暴力,由此导致的所有后果请自行承担**

*有投诉随时跑路*

### 准备工作

- 电脑端浏览器(Edge Chrome Firefox)
- JDK1.8+
- 记得PU平台账号密码或会复制cookies
- 会用记事本修改.properties配置文件
- github能够正常访问（程序需要连接github读取网络配置）

下载Release中最新的PUSeckill.zip, 解压缩

### 完成网页登录

#### A.或使用账号密码自动登录

- 修改setting.properties配置文件
  
  mySchedule=（严格按照格式）
  
  Student_ID=（学号即可，不要选择输入账号）
  
  Password=（必须正确，记不住的先重置一次密码）

#### B.或复制浏览器的cookies

- 浏览器登录完成->按键盘F12选择Console(控制台)->输入> document.cookie复制
  
  cookies不包含引号 cookies失效期为10小时(Hm_lvt是广告没用)

- 修改setting.properties配置文件
  
  useLocalCookies=true
  
  myCookies=（复制的cookies）

### 获得活动编号

- 点入要参加的活动
  
  > https://pc.pocketuni.net/active/detail?id=xxxxx

- activityID_1为 id后的内容

- 修改setting.properties配置文件
  
  activityID_1=（活动id）

### 快速启动

配置好setting.properties文件后直接双击run.bat启动程序，程序会全程自动运行，无需任何人为干预，运行期间不要关闭运行窗口

### 提示

- 除上面提到的参数需要自行修改，其余所有参数无需修改也不可修改，否则程序无法运行

- 定时时间一定要严格按照示例格式输入，使用24小时制！！！
