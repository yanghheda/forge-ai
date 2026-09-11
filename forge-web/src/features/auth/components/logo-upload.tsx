import { Image as ArcoImage, Upload } from "@arco-design/web-react";

import { useFilePreview } from "../hooks/use-file-preview";
import styles from "./auth-forms.module.css";

export function LogoUpload({ value, error, onFileChange }: { value?: File | null; error?: boolean; onFileChange?: (file: File | null) => void }) {
  const previewUrl = useFilePreview(value);

  function update(file: File | null) {
    onFileChange?.(file);
  }

  return (
    <Upload
      className={`${styles.logoUpload} ${error ? styles.logoUploadError : ""}`}
      drag
      autoUpload={false}
      accept={{ type: "image/webp,.webp", strict: true }}
      limit={1}
      fileList={[]}
      showUploadList={false}
      tip="仅支持 WebP 格式，比例 1:1，文件不超过 2 MiB"
      onChange={(_fileList, file) => update(file.originFile ?? null)}
    >
      {value && previewUrl ? (
        <div className={styles.logoSelected} role="button" tabIndex={0} aria-label="重新上传公司 Logo">
          <ArcoImage src={previewUrl} alt={`${value.name} Logo`} width="100%" height="100%" preview={false} />
          <span className={styles.logoReplace}>重新上传</span>
        </div>
      ) : undefined}
    </Upload>
  );
}
