import getQuery from "../../helper/getQuery";

//TODO: Check after.

export const getRequestRejected = async (utc : Promise<string>, request_id : string) : Promise<any> => {
    const sql = `
        SELECT ur.description, CONVERT_TZ(ur.rejected_date, '+00:00', ?) as rejected_date
            FROM Users_Rejected ur
            WHERE ur.user_registration_id = ?`;
    
    const params = [utc, request_id];
    return await getQuery(sql, params);
}