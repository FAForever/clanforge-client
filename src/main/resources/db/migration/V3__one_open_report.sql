--one open report per person per clan, ReportService checks first and this index holds when two arrive at the same moment
CREATE UNIQUE INDEX clan_report_one_open ON clan_report (clan_id, reporter_id) WHERE state = 'OPEN';
