import getQuery from "../../helper/getQuery";

export const getCataloguesName = async (table_name : string, name : string, table_id : string | null = null) : Promise<any> => {
    const sql_without_table = `
        SELECT id, name 
            FROM ${table_name} 
        WHERE name LIKE ?
            AND status = 'Allowed'`;

    let sql_with_table = `
        SELECT c.*
            FROM (
                SELECT c.id, c.name 
                    FROM ${table_name} c
                WHERE status = 'Allowed'
                UNION
                SELECT tn.id, tn.name
                    FROM ${table_name} tn
                        JOIN Table_${table_name} twn ON `;

    if(table_id) {
        switch(table_name) {
            case 'Tags': {
                sql_with_table += `
                    twn.tag_id = tn.id`;
                break;
            }
            case 'Systems': {
                sql_with_table += `
                    twn.system_id = tn.id`;
                break;
            }
            case 'Platforms': {
                sql_with_table += `
                    twn.platform_id = tn.id`;
                break;
            }
        }

        sql_with_table += `
            WHERE twn.table_id = ?
            ) as c
            WHERE c.name LIKE ?`;

        const params = [table_id, `${name}%`];
        return await getQuery(sql_with_table, params);
    }

    const params = [`${name}%`];
    return await getQuery(sql_without_table, params);
}