import getQuery from "../../helper/getQuery";

export const getPublicFileFromTable = async () => {
    const sql = `
        SELECT id, name, mine, size
            FROM Files f
            WHERE f.type_file = 'Public'
                AND f.status = 'Current'`;

    const response = await getQuery(sql);
    return response;
}
