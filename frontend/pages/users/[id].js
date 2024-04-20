import TableComponent from "@/components/general/TableComponent";

export const getServerSideProps = ({params}) => {
    const { id } = params;
    const user = {
        id: 1,
        name: 'Teshynil',
        discord: 'Teshynil#0001',
        joined_table: {
            inscrited_table: 10,
            active_table: 3,
            candidate_table: 4
        },
        master_table: {
            active_master_table: 3,
            created_master_table: 5,
        },
        comments: {
            positive_comments: 3,
            neutral_comments: 2,
            negative_comments: 7
        },
        karma: 1,
        country: 'Mexico',
        timezone: 'UTC-06:00'
    }

    return {
        props: {
            user: user
        }
    }
}

export default function UserInfo (props) {
    console.log(props)

    return (
        <>
            xd
        </>
    );
}