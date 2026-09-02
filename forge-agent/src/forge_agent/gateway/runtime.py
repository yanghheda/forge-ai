"""Agent Runtime 可替换边界。"""

from __future__ import annotations

from typing import Protocol


class RuntimeGateway(Protocol):
    """后续运行图实现必须遵守的最小边界；本轮不执行 Agent Run。"""

    def is_available(self) -> bool:
        """报告 Runtime 适配器是否完成基础初始化。"""

        ...
