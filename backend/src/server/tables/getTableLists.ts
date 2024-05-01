import getQuery from "../../helper/getQuery";

export const getTablesLists = async (user_id : string) : Promise<any> => {
    const sql = `
        SELECT t.id, t.name, GROUP_CONCAT(u.name) as masters, pt.players, t.status, t.table_type 
            FROM Tables t
                JOIN Masters m ON m.table_id = t.id 
                JOIN Users u ON u.id = m.user_master_id 
                JOIN Players_Table pt ON pt.table_id = t.id 
            WHERE m.master_type = 'Owner'
                AND u.id = ?
                AND t.status != 'Deleted'
            GROUP BY t.id`;

        const params = [user_id];
        return await getQuery(sql, params);
}