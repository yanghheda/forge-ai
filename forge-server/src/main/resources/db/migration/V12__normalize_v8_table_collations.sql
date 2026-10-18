-- 修正 V8 迁移遗漏的字符集排序规则声明。requirement_executors 与 requirement_review_votes
-- 建表时未声明 COLLATE，会继承数据库默认排序规则；当库默认不是 utf8mb4_0900_ai_ci 时，
-- 这两张表的 role_code 与 roles.code（历史迁移已显式钉定为 utf8mb4_0900_ai_ci）比较会触发
-- "Illegal mix of collations"，使任务创建候选需求接口（ConsoleMapper.taskCreationOptions）
-- 返回 500，前端因此显示为“没有可创建任务的需求”。
-- 这里把两张表统一收敛到与其余表一致的排序规则；对已经使用该排序规则的库是无操作重建。

ALTER TABLE `requirement_executors`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE `requirement_review_votes`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
