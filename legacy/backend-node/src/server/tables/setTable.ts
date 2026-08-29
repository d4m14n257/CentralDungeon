import { PoolConnection } from "mysql2/promise";
import { Table } from "../../models/models";
import generateBase64 from "../../helper/generateBase64";
import setQuery from "../../helper/setQuery";

export const setTable = async (table : Table, query : PoolConnection) : Promise<any> => {
    const id : string = await generateBase64()
    .then((data) => data[0][0].generated_id);

    const now = new Date();

    const sql = `
        INSERT INTO Tables (id, name, description, permitted, startdate, timezone, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)`;

    const params = [id, table.name, table.description, table.permitted, table.startdate, table.timezone, now];
    const response = await setQuery(sql, params, query);

    return await {...response, id: id};
}