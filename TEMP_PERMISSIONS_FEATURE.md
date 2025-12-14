# 臨時資料庫權限管理功能

## 功能概述

這個新功能提供了一個簡化的介面，用於動態授予和管理具有時間限制的資料庫權限。管理員可以快速為用戶分配臨時資料庫訪問權限，系統會自動在到期時撤銷這些權限。

## 主要特性

### 1. 快速時間預設選項
- **1天** - 短期臨時訪問
- **3天** - 中期項目訪問
- **1周** - 較長期的任務訪問
- 也支持自定義天數

### 2. 批量權限分配
- 一次操作可以為單個用戶分配多個資料庫的多種權限
- 支持選擇多個資料庫/資源
- 支持選擇多種權限類型（READ, WRITE, DELETE, EXECUTE, ADMIN）

### 3. 自動撤銷機制
- 利用MariaDB事件調度器自動撤銷過期權限
- 無需手動干預，確保安全性

### 4. 權限管理功能
- **查詢**：按用戶或狀態篩選查看現有權限
- **延長**：支持快速延長權限（1天/3天/1周）
- **撤銷**：可立即撤銷不再需要的權限

## 技術實現

### 新增文件

#### 後端
1. **TemporaryDbPermissionController.java**
   - 路徑：`/temp-permissions`
   - 主要端點：
     - `GET /temp-permissions` - 顯示管理頁面
     - `POST /temp-permissions/grant-bulk` - 批量授予權限
     - `POST /temp-permissions/{id}/extend` - 延長權限
     - `POST /temp-permissions/{id}/cancel` - 撤銷權限
     - `GET /temp-permissions/user/{userId}/active` - 查詢用戶活躍權限（AJAX）

2. **BulkPermissionRequest.java**
   - DTO類，用於批量權限請求
   - 包含驗證規則

3. **PermissionService.java** (更新)
   - 新增 `getPermissionsByStatus()` 方法

#### 前端
1. **manage.html**
   - 路徑：`src/main/resources/templates/temp-permissions/manage.html`
   - 完整的臨時權限管理界面
   - 包含批量授予表單和權限列表視圖

2. **layout.html** (更新)
   - 導航欄新增「臨時權限」入口

## 使用流程

### 授予臨時權限

1. 登入系統（需要ADMIN角色）
2. 點擊導航欄的「臨時權限」
3. 在授予權限表單中：
   - 選擇目標用戶
   - 選擇有效期限（使用快速按鈕或輸入自定義天數）
   - 輸入資料庫/資源名稱（可添加多個）
   - 勾選需要的權限類型（可多選）
   - 可選：添加說明
4. 點擊「授予權限並自動激活」

### 查詢現有權限

1. 使用篩選器：
   - 按用戶篩選
   - 按狀態篩選（待批准/已批准/激活中/已過期/已撤銷）
2. 查看權限卡片，顯示：
   - 用戶名稱
   - 資源名稱
   - 權限類型
   - 開始和到期時間
   - 當前狀態

### 延長權限

1. 在激活中的權限卡片上
2. 點擊「延長」下拉菜單
3. 選擇延長時間（1天/3天/7天）
4. 系統自動更新到期時間並重新創建MariaDB撤銷事件

### 撤銷權限

1. 在激活中的權限卡片上
2. 點擊「立即撤銷」按鈕
3. 確認操作
4. 系統立即撤銷MariaDB權限並取消計劃的撤銷事件

## 權限類型說明

- **READ** - SELECT權限，只能查詢數據
- **WRITE** - SELECT, INSERT, UPDATE權限，可以讀寫數據
- **DELETE** - SELECT, DELETE權限，可以刪除數據
- **EXECUTE** - EXECUTE權限，可以執行存儲過程
- **ADMIN** - ALL PRIVILEGES，完全管理權限

## 安全特性

1. **僅管理員可授予** - 只有ROLE_ADMIN角色可以授予權限
2. **必須設定到期時間** - 所有臨時權限都必須有明確的結束時間
3. **自動撤銷** - 通過MariaDB事件確保權限準時撤銷
4. **審計日誌** - 所有操作都記錄在permission_events表中
5. **輸入驗證** - 表單包含完整的驗證規則

## 工作流程

```
1. 管理員提交批量權限請求
   ↓
2. 系統為每個資源×權限類型組合創建Permission記錄
   ↓
3. 自動批准並激活權限
   ↓
4. MariaDBEventService創建資料庫用戶（如不存在）
   ↓
5. 授予MariaDB權限
   ↓
6. 創建MariaDB事件，在到期時自動撤銷
   ↓
7. 到期時：
   - MariaDB事件自動執行REVOKE
   - 定時任務標記Permission狀態為EXPIRED
   - 記錄PermissionExpiredEvent
```

## 數據庫影響

### 使用現有表結構
- `permissions` - 存儲權限記錄
- `permission_events` - 記錄所有權限事件
- `users` - 用戶信息
- MariaDB系統表 - 用於創建事件調度器

### MariaDB事件命名規範
```
revoke_perm_{permission_id}_{username}_{timestamp}
```

## 配置要求

### application.yml
確保以下配置已啟用：

```yaml
app:
  permission:
    check-interval: 300000  # 5分鐘檢查過期權限
    cleanup-expired: true    # 自動清理
    grace-period: 60000      # 1分鐘寬限期
```

### MariaDB
- 事件調度器必須啟用：`SET GLOBAL event_scheduler = ON;`
- 系統會在啟動時自動檢查並啟用

## 測試建議

### 功能測試
1. 授予單個權限
2. 授予批量權限（多個資源×多種權限類型）
3. 按用戶篩選
4. 按狀態篩選
5. 延長權限（測試MariaDB事件是否更新）
6. 立即撤銷權限
7. 等待權限自動過期（可以設置1分鐘的測試權限）

### 安全測試
1. 非管理員用戶無法訪問授予功能
2. 輸入驗證（空值、負數、無效資源名等）
3. SQL注入防護（resourceName字段）

## 未來增強建議

1. **權限模板** - 預定義常用權限組合
2. **批准工作流** - 可選的多級審批
3. **通知系統** - 權限即將過期時提醒用戶和管理員
4. **使用報告** - 統計權限使用情況
5. **資源瀏覽器** - 從現有資料庫列表中選擇而非手動輸入

## 故障排除

### 權限未自動撤銷
- 檢查MariaDB事件調度器狀態：`SHOW VARIABLES LIKE 'event_scheduler';`
- 查看現有事件：`SHOW EVENTS;`
- 檢查應用日誌中的錯誤信息

### 無法授予權限
- 確認MariaDB連接配置正確
- 確認應用資料庫用戶有CREATE USER和GRANT權限
- 檢查資源名稱是否有效

### 延長功能不工作
- 確認只有ACTIVE狀態的權限可以延長
- 檢查新的結束時間是否晚於當前結束時間
- 查看日誌確認MariaDB事件是否成功重新創建

## 文件清單

### 新增文件
- `src/main/java/com/userpermission/management/controller/TemporaryDbPermissionController.java`
- `src/main/java/com/userpermission/management/dto/BulkPermissionRequest.java`
- `src/main/resources/templates/temp-permissions/manage.html`

### 修改文件
- `src/main/java/com/userpermission/management/service/PermissionService.java`
- `src/main/resources/templates/fragments/layout.html`

## 作者與維護

此功能由Claude AI助手開發，作為資料庫用戶訪問管理系統的增強功能。

---

**版本**: 1.0.0
**創建日期**: 2025-12-14
**依賴**: Spring Boot 3.2.1, MariaDB/MySQL
