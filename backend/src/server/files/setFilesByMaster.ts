import { PoolConnection } from "mysql2/promise";
import generateBase64 from "../../helper/generateBase64";
import setQuery from "../../helper/setQuery";

export const setFilesByMaster = async (user_id : string, file : Express.Multer.File, type: string, query : PoolConnection) => {
    const id : string = await generateBase64()
    .then((data) => data[0][0].generated_id);

    const sql = `
        INSERT INTO Files (id, name, source, mine, user_created_id, type_file)
            VALUE (?, ?, ?, ?, ?, ?)`;

    const params = [id, file.originalname, file.path, file.mimetype, user_id, type];
    const response = await setQuery(sql, params, query);

    return {...response, id: id};
}