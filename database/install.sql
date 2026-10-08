-- SQL Server 2017+. Select an existing EMPTY database before running.
-- In SSMS, select your empty database, then enable Query > SQLCMD Mode.
-- Update the five include paths below if you move these files.
-- From a terminal in this directory:
-- sqlcmd -S .\SQLEXPRESS -E -C -d YourDatabase -b -f 65001 -i install.sql
-- schema.sql creates tables once. The four object files use CREATE OR ALTER
-- and can be rerun individually without recreating or deleting table data.
:ON ERROR EXIT
:r "D:\hk1_nam3\ltweb\TICKETSCENTER\schema.sql"
:r "D:\hk1_nam3\ltweb\TICKETSCENTER\functions.sql"
:r "D:\hk1_nam3\ltweb\TICKETSCENTER\views.sql"
:r "D:\hk1_nam3\ltweb\TICKETSCENTER\procedures.sql"
:r "D:\hk1_nam3\ltweb\TICKETSCENTER\triggers.sql"
