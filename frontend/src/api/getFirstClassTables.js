export const getFirstClassTables = async (user_id) => {
    try {
        const response = await fetch(`${process.env.NEXT_PUBLIC_SERVER}/tables/first-class-tables/${user_id}`).catch((err) => {
            throw {...err.cause, status: 500}
        });

        const status = response.status;
        const data = await response.json();

        if(status == 200)
            return data;
        else
            throw {...data, status: status};

    } catch (err) {  
        return err;
    }
}