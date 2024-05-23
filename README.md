# 查看充电桩使用情况

## 获取 token

* 打开浏览器
* 启动 `F12` 浏览器工具
* 打开网页：https://api.issks.com/issksh5/?#/pages/phoneLogin/phoneLogin?goType=2
* 登录，并查看 `/user/v1/common/auth/issks/register` 请求的响应体，例如：
```json
{
    "code": "1",
    "msg": "注册成功",
    "data": "issks_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "success": true
}
```
* 记录 token，即上面 `data` 的值，例如：`issks_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
* 将 token 写到 `token.json` 文件中
