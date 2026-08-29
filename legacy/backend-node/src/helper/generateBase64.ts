import { ResultSetHeader } from "mysql2";
import getQuery from "./getQuery";

export default async function generateBase64() : Promise<any> {
    const sql = `
        CALL generate_base64_id(${process.env.LENGTH});`;

    const response = await getQuery(sql);

    return response;
}