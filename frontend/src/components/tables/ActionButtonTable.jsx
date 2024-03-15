import { useRouter } from "next/router";

import IconButton from '@mui/material/IconButton';

import VisibilityIcon from '@mui/icons-material/Visibility';

export default function ActionButtonTable (props) {
    const { id } = props;
    const router = useRouter();

    const handleTableRoute = (id) => {
        router.push(`/tables/${id}`)
    }

    return (
        <IconButton onClick={() => {handleTableRoute(id)}} size='small'>
            <VisibilityIcon fontSize="inherit"/>
        </IconButton>
    )
}