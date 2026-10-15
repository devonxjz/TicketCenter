#!/usr/bin/env bash
# ============================================================================
# export_bacpac.sh
#
# TicketsCenter Database Management - BACPAC Export & Import Script
# Supports: Azure SQL Database (ticketscenter.database.windows.net) & Local SQL Server
#
# Usage:
#   ./export_bacpac.sh [export|import] [output_or_source_file]
#
# Examples:
#   ./export_bacpac.sh export ./TicketsCenter_Backup.bacpac
#   ./export_bacpac.sh import ./TicketsCenter_Backup.bacpac
# ============================================================================

set -euo pipefail

ACTION="${1:-export}"
TARGET_FILE="${2:-./TicketsCenter_$(date +%Y%m%d_%H%M%S).bacpac}"

# Connection configuration from environment variables or defaults
SQL_HOST="${TC_SQL_HOST:-ticketscenter.database.windows.net}"
SQL_PORT="${TC_SQL_PORT:-1433}"
SQL_DB="${TC_SQL_DEV_DB:-TicketsCenter}"
SQL_USER="${TC_SQL_LOGIN:-ticketscenteradmin}"
SQL_PASSWORD="${TC_SQL_PASSWORD:-}"

if [[ -z "${SQL_PASSWORD}" ]]; then
    echo "⚠️  Environment variable TC_SQL_PASSWORD is not set."
    read -rsp "Enter SQL Password for ${SQL_USER}@${SQL_HOST}: " SQL_PASSWORD
    echo ""
fi

# Locate or check sqlpackage
SQLPACKAGE_BIN=""
if command -v sqlpackage >/dev/null 2>&1; then
    SQLPACKAGE_BIN="sqlpackage"
elif [[ -f "/usr/local/bin/sqlpackage" ]]; then
    SQLPACKAGE_BIN="/usr/local/bin/sqlpackage"
elif [[ -f "$HOME/.dotnet/tools/sqlpackage" ]]; then
    SQLPACKAGE_BIN="$HOME/.dotnet/tools/sqlpackage"
fi

execute_sqlpackage() {
    local cmd="$1"
    if [[ -n "${SQLPACKAGE_BIN}" ]]; then
        eval "${SQLPACKAGE_BIN} ${cmd}"
    elif command -v docker >/dev/null 2>&1; then
        echo "ℹ️  Local sqlpackage not found. Executing via Microsoft SQLPackage Docker container..."
        local abs_dir
        abs_dir="$(cd "$(dirname "${TARGET_FILE}")" && pwd)"
        local base_file
        base_file="$(basename "${TARGET_FILE}")"
        
        docker run --rm \
            -v "${abs_dir}:/workspace" \
            mcr.microsoft.com/azure-sql-edge:latest \
            /opt/mssql-tools/bin/sqlcmd --help >/dev/null 2>&1 || true
        
        echo "Please install sqlpackage via dotnet: dotnet tool install -g microsoft.sqlpackage"
        exit 1
    else
        echo "❌ sqlpackage CLI is not installed."
        echo "Install via .NET tool:"
        echo "  dotnet tool install -g microsoft.sqlpackage"
        exit 1
    fi
}

echo "======================================================================"
echo "TicketsCenter BACPAC Manager"
echo "Action:   ${ACTION}"
echo "Server:   ${SQL_HOST}:${SQL_PORT}"
echo "Database: ${SQL_DB}"
echo "User:     ${SQL_USER}"
echo "File:     ${TARGET_FILE}"
echo "======================================================================"

case "${ACTION}" in
    export)
        echo "⏳ Starting Export to BACPAC: ${TARGET_FILE}..."
        execute_sqlpackage "/Action:Export \
            /SourceServerName:\"${SQL_HOST},${SQL_PORT}\" \
            /SourceDatabaseName:\"${SQL_DB}\" \
            /SourceUser:\"${SQL_USER}\" \
            /SourcePassword:\"${SQL_PASSWORD}\" \
            /TargetFile:\"${TARGET_FILE}\" \
            /p:CommandTimeout=1200"
        echo "✅ Export completed successfully: ${TARGET_FILE}"
        ls -lh "${TARGET_FILE}"
        ;;

    import)
        if [[ ! -f "${TARGET_FILE}" ]]; then
            echo "❌ Error: Import file not found: ${TARGET_FILE}"
            exit 1
        fi
        echo "⏳ Starting Import from BACPAC into ${SQL_DB}..."
        execute_sqlpackage "/Action:Import \
            /TargetServerName:\"${SQL_HOST},${SQL_PORT}\" \
            /TargetDatabaseName:\"${SQL_DB}\" \
            /TargetUser:\"${SQL_USER}\" \
            /TargetPassword:\"${SQL_PASSWORD}\" \
            /SourceFile:\"${TARGET_FILE}\" \
            /p:CommandTimeout=1200"
        echo "✅ Import completed successfully into ${SQL_DB}."
        ;;

    *)
        echo "❌ Invalid action: ${ACTION}. Use 'export' or 'import'."
        exit 1
        ;;
esac
