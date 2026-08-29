import { PoolConnection } from "mysql2/promise";
import generateBase64 from "../../helper/generateBase64";
import { Platforms, Systems, Tags } from "../../models/models";
import setQuery from "../../helper/setQuery";

export async function setCatalogue (data : Tags | Systems | Platforms, table_name: string, query : PoolConnection) : Promise<any> {
    const id : string = await generateBase64()
    .then((data) => data[0][0].generated_id);

    const sql = `
        INSERT INTO ${table_name} (id, name) VALUES (?, ?)`;

    const params = [id, data.name];
    const response = await setQuery(sql, params, query);

    return await {...response, id: id};
}