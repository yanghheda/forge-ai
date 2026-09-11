import { useEffect, useState } from "react";

export function useFilePreview(file?: File | null) {
  const [previewUrl, setPreviewUrl] = useState<string>();

  useEffect(() => {
    if (!file) return;
    let active = true;
    const reader = new FileReader();
    reader.onload = () => active && setPreviewUrl(String(reader.result));
    reader.onerror = () => active && setPreviewUrl(undefined);
    reader.readAsDataURL(file);
    return () => {
      active = false;
      reader.abort();
    };
  }, [file]);

  return file ? previewUrl : undefined;
}
