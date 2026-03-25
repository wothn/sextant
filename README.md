# Sextant

Sextant 是一个基于 **React Native + Expo** 的个人记账应用，当前聚焦本地优先的记账体验，支持：

- 支出 / 收入记录
- 账户管理
- 转账记录
- 月度预算
- 标签系统
- CSV 导出备份

UI 采用 `react-native-paper`（Material 3），数据层使用 `expo-sqlite` 本地存储。

## 功能概览

- **极速记账**：面向高频输入场景，优先保证录入速度
- **账户维度管理**：现金、银行卡、钱包等账户统一管理
- **预算提醒基础能力**：按分类维护月预算
- **本地数据导出**：支持导出交易 CSV 进行备份
- **离线可用**：当前不依赖云同步

## 技术栈

- `Expo 55`
- `React 19`
- `React Native 0.83`
- `Expo Router`
- `react-native-paper`
- `expo-sqlite`
- `Zustand`
- `Jest + @testing-library/react-native`
- `TypeScript`
- `Oxlint + oxfmt`

## 快速开始

### 环境要求

- Node.js 20+
- `pnpm` 10+

### 安装依赖

```bash
pnpm install
```

### 启动项目

```bash
pnpm start
pnpm android
pnpm ios
```

## 常用命令

```bash
pnpm lint
pnpm lint:fix
pnpm typecheck
pnpm test
pnpm test:coverage
pnpm fmt:check
pnpm fmt
```

## 项目结构

```text
app/                    Expo Router 页面
  _layout.tsx           根布局：初始化数据库、注入 PaperProvider
  (tabs)/               底部标签页页面

src/
  db/                   SQLite 客户端与 schema 初始化
  features/             业务服务（transactions / budgets / tags）
  lib/backup/           CSV 导出能力
  store/                Zustand UI 状态
  test/                 测试辅助工具
  types/                领域模型类型定义
```

## 开发约定

- 使用 `@/*` 路径别名访问根目录文件
- 业务服务统一通过 `getDb()` 访问数据库
- TypeScript 开启 `strict`
- 优先补测试，再扩展功能
- 代码格式由 `oxfmt` 负责，静态规则由 `Oxlint` 负责

## 测试与质量门槛

当前仓库已配置：

- 类型检查：`pnpm typecheck`
- 单元 / 页面测试：`pnpm test`
- 覆盖率检查：`pnpm test:coverage`
- 代码格式检查：`pnpm fmt:check`
- 静态规则检查：`pnpm lint`

覆盖率门槛：

- Branches: `55%`
- Functions: `70%`
- Lines: `70%`
- Statements: `70%`

## 当前范围说明

这个项目目前以 **本地优先 MVP** 为主：

- 重点做 Android / Expo 开发体验
- 暂不包含云同步
- 暂不配置 CI / 发布构建流程

后续若进入分发阶段，可以再补充 EAS Build、版本发布与自动化流程。
