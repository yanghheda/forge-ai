/// <reference types="vite/client" />

interface ImportMetaEnv {
  /* 可选的前端 API 基地址覆盖；未设置时统一使用同源 /api 前缀。 */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  /* Vite 注入的编译期环境变量。 */
  readonly env: ImportMetaEnv;
}
