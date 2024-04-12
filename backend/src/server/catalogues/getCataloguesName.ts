import getQuery from "../../helper/getQuery";

export const getCataloguesName = async (table_name : string, name : string) => {
    const sql = `SELECT id, name FROM ${table_name} WHERE name LIKE ? AND status = 'Allowed'`;

    const params = [`${name}%`];
    return await getQuery(sql, params);
}