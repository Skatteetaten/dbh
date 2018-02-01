select id, name, schema_type from SCHEMA_DATA where id in (
  select schema_id
  from LABELS where name in ('affiliation', 'application', 'environment', 'name')
  group by schema_id
  HAVING listagg(value, ',') WITHIN GROUP (ORDER BY name) like 'paas,boober,paas-boober,referanseapp'
);