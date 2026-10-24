-- Existing quote writers keep explicit read access. Other request readers no longer inherit quotes.
UPDATE roles SET permissions=REPLACE(permissions,'"quote:write"','"quote:write","quote:read"') WHERE permissions LIKE '%"quote:write"%' AND permissions NOT LIKE '%"quote:read"%';
UPDATE roles SET permissions=REPLACE(permissions,'"request:approve"','"request:approve","quote:read"') WHERE code='boss' AND permissions LIKE '%"request:approve"%' AND permissions NOT LIKE '%"quote:read"%';
