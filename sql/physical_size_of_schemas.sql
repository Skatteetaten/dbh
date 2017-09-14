select
   owner,
   sum(bytes)/1024/1024 schema_size_mb
from
   dba_segments
group by
   owner;