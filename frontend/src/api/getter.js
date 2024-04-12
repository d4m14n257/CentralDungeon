export const getter = async (user_id, url) => {
    try {
        const response = await fetch(`${process.env.NEXT_PUBLIC_SERVER}/${url}/${user_id}`).catch((err) => {
            console.log(err)
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