import pytest

from ledger_fuzz.client import LedgerClient
from ledger_fuzz.config import Config
from ledger_fuzz.db import LedgerDatabase


@pytest.fixture(scope="session")
def config() -> Config:
    return Config.from_env()


@pytest.fixture(scope="session")
def ledger_client(config: Config):
    client = LedgerClient(config.ledger_base_url)
    yield client
    client.close()


@pytest.fixture(scope="session")
def ledger_db(config: Config):
    db = LedgerDatabase(config.database_url)
    yield db
    db.close()
