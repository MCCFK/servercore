>DeepSeek 生成

## 准备工作

### 1. 安装 Git

下载地址：https://git-scm.com/downloads

安装时一路默认即可。

### 2. 配置用户名和邮箱

打开 **Git Bash**，执行：

```bash
git config --global user.name "你的GitHub用户名"
git config --global user.email "你的GitHub邮箱"
```

### 3. 添加 SSH Key（免密推送）

Git Bash 里执行：

```bash
ssh-keygen -t rsa -b 4096 -C "你的邮箱"
```

一路回车。然后：

```bash
cat ~/.ssh/id_rsa.pub
```

复制输出的内容。去 GitHub → Settings → SSH and GPG keys → New SSH key，粘贴保存。

---

## 获取代码

第一次下载代码，Git Bash 里执行：

```bash
git clone git@github.com:MCCFK/servercore.git
```

---

## 修改代码后提交

### 完整流程（三步走）

```bash
# 1. 添加所有改动
git add .

# 2. 提交并写清楚改了什么
git commit -m "修复xxbug / 添加xx功能"

# 3. 推送到 GitHub
git push
```

### 如果 push 失败

说明远程有别人先推送了，先拉取再推送：

```bash
git pull
git push
```

---

## 如果你使用 AI

直接告诉 AI 以下信息，它就能帮你操作 Git：

```
项目路径：XXX
远程仓库：https://github.com/MCCFK/servercore.git

先 git pull 拉取最新
改完代码后执行：
  git add .
  git commit -m "做了什么"
  git push
```


---

## 一句话总结

```
git pull          → 先拉取最新
改代码
git add .         → 添加改动
git commit -m "xx" → 提交
git push          → 推送
```
>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>