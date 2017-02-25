-- name: create-company-table!
-- Create a silly company table
CREATE TABLE company (
  id INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY,
  name VARCHAR(200),
  -- Fields for visiting address
  visiting_street_address varchar(200),
  visiting_postal_code varchar(10),
  visiting_country char(2),
  -- fields for billing address
  billing_street_address varchar(200),
  billing_postal_code varchar(10),
  billing_country char(2)
)

-- name: companies-in-country
-- Return companies and process the result set to have namespaced keys
-- and nested maps as specified by the AS names. The query parameter is
-- also a namespaced key
SELECT -- company fields as namespaced keys
       id AS "::company/id",
       name AS "::company/name",
       -- visiting and billing addresses as nested maps
       visiting_street_address AS "[::company/visiting-address ::address/street]",
       visiting_postal_code AS "[::company/visiting-address ::address/postal-code]",
       visiting_country AS "[::company/visiting-address ::address/country]",
       billing_street_address AS "[::company/billing-address ::address/street]",
       billing_postal_code AS "[::company/billing-address ::address/postal-code]",
       billing_country AS "[::company/billing-address ::address/country]"
  FROM company
 WHERE -- query parameter as a namespaced key
       visiting_country = ::address/country
