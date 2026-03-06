# 优化家 - 移动客户端 (youhuajia-app)

基于 uni-app (Vue 3) 的跨平台客户端，支持 H5 和微信小程序。

## 环境要求

- Node.js 22（项目根目录 `.nvmrc` 指定）
- npm 10+
- 微信开发者工具（微信小程序调试时需要）

## 安装依赖

```bash
cd youhuajia-app
npm install
```

### 国内镜像加速

npm 默认源在国内较慢，建议配置镜像：

```bash
# 方式一：临时使用淘宝镜像安装
npm install --registry=https://registry.npmmirror.com

# 方式二：全局切换为淘宝镜像
npm config set registry https://registry.npmmirror.com

# 方式三：使用 nrm 管理多个源（推荐）
npm install -g nrm
nrm use taobao
npm install

# 恢复官方源
nrm use npm
```

验证当前 registry：

```bash
npm config get registry
```

> Sass 等二进制包可能需要额外配置镜像，如遇 `node-sass` / `sharp` 等安装失败：
>
> ```bash
> npm config set sass_binary_site https://npmmirror.com/mirrors/node-sass
> ```

## 开发调试

### H5（浏览器）

```bash
npm run dev:h5
```

启动后访问 `http://localhost:3000`，API 请求自动代理到 `http://localhost:8080`（需要后端服务运行）。

### 微信小程序

```bash
npm run dev:mp-weixin
```

编译产物在 `dist/dev/mp-weixin/`，用微信开发者工具打开该目录即可预览调试。

> 注意：`manifest.json` 中 `mp-weixin.appid` 需填入真实的小程序 AppID 才能在真机上调试。

## 构建打包

### H5 生产构建

```bash
npm run build:h5
```

产物目录：`dist/build/h5/`，可直接部署到 Nginx 等静态服务器。

Nginx 参考配置：

```nginx
server {
    listen 80;
    server_name youhuajia.example.com;
    root /path/to/dist/build/h5;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend-server:8080;
    }
}
```

### 微信小程序生产构建

```bash
npm run build:mp-weixin
```

产物目录：`dist/build/mp-weixin/`，通过微信开发者工具上传提审。

## 项目结构

```text
youhuajia-app/
├── index.html              # H5 入口
├── package.json
├── vite.config.js
└── src/
    ├── main.js             # 应用入口
    ├── App.vue             # 根组件
    ├── manifest.json       # uni-app 配置（AppID、代理等）
    ├── pages.json          # 路由/页面配置
    ├── uni.scss            # 全局 SCSS 变量注入
    ├── api/                # API 接口层
    ├── components/         # 共享组件
    ├── pages/              # 页面（9 页漏斗 + 登录 + 低分路径）
    ├── stores/             # Pinia 状态管理
    ├── styles/             # 全局样式/变量
    └── utils/              # 工具函数
```

## 关键配置说明

| 配置项       | 文件                                       | 说明                         |
| ------------ | ------------------------------------------ | ---------------------------- |
| API 代理     | `src/manifest.json` → `h5.devServer.proxy` | 开发环境将 `/api` 代理到后端 |
| 小程序 AppID | `src/manifest.json` → `mp-weixin.appid`    | 上线前必须填入               |
| 页面路由     | `src/pages.json`                           | 所有页面路径和导航栏配置     |
| 主题色       | `src/styles/variables.scss`                | 全局颜色/间距/字号变量       |

## npm scripts 速查

| 命令                     | 用途               |
| ------------------------ | ------------------ |
| `npm run dev:h5`         | H5 开发服务器      |
| `npm run build:h5`       | H5 生产构建        |
| `npm run dev:mp-weixin`  | 微信小程序开发编译 |
| `npm run build:mp-weixin`| 微信小程序生产构建 |
