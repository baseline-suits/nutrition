#!/usr/bin/env python3
import argparse
import secrets
from datetime import timedelta

from baseline_api.main import cleanup_expired_uploads, db, digest, iso, migrate, now, uid


def main() -> None:
    parser = argparse.ArgumentParser(description="Baseline-Administration")
    subcommands = parser.add_subparsers(dest="command", required=True)
    invite = subcommands.add_parser("create-access-code")
    invite.add_argument("--hours", type=int, default=72)
    subcommands.add_parser("cleanup-uploads")
    args = parser.parse_args()
    if args.command == "create-access-code":
        if not 1 <= args.hours <= 8760:
            parser.error("--hours muss zwischen 1 und 8760 liegen")
        migrate()
        code = secrets.token_urlsafe(32)
        with db() as connection:
            connection.execute(
                "INSERT INTO access_codes VALUES (?,?,?,?,NULL,NULL,NULL)",
                (uid(), digest(code), iso(now()), iso(now() + timedelta(hours=args.hours))),
            )
            connection.commit()
        print(code)
    elif args.command == "cleanup-uploads":
        print(cleanup_expired_uploads())


if __name__ == "__main__":
    main()
