import pytest

from baseline_api import main


@pytest.fixture(autouse=True)
def reset_in_memory_limits():
    main.auth_limit.entries.clear()
    yield
    main.auth_limit.entries.clear()
