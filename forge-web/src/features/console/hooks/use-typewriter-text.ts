"use client";

import { useEffect, useRef, useState } from "react";

const DEFAULT_INTERVAL_MILLIS = 18;

interface TypewriterState {
  key: string;
  value: string;
}

export function useTypewriterText(target: string, key: string, intervalMillis = DEFAULT_INTERVAL_MILLIS) {
  const [state, setState] = useState<TypewriterState>({ key, value: "" });
  const targetRef = useRef(target);

  useEffect(() => {
    targetRef.current = target;
  }, [target]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setState((current) => {
        const targetCharacters = Array.from(targetRef.current);
        if (current.key !== key) {
          return { key, value: targetCharacters.slice(0, 1).join("") };
        }
        const currentLength = Array.from(current.value).length;
        if (currentLength >= targetCharacters.length) return current;
        return { key, value: targetCharacters.slice(0, currentLength + 1).join("") };
      });
    }, intervalMillis);
    return () => window.clearInterval(timer);
  }, [intervalMillis, key]);

  return state.key === key ? state.value : "";
}
