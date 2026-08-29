import { useState, useRef } from "react";

import IconButton from '@mui/material/IconButton';

import InfoIcon from '@mui/icons-material/Info';
import BlockIcon from '@mui/icons-material/Block';

import UserModalInfo from "@/components/users/modal/UserModalInfo";


export default function ActionButtonUser (props) {
    const { value } = props;
    const [ userInfo, setUserInfo ] = useState(false);
    const user = useRef();

    const handleOpenUserInfo = (value) => {
        user.current = value;
        setUserInfo(true);
    }

    const handleCloseUserInfo = () => {
        setUserInfo(false);
    }

    return (
        <>
            <IconButton onClick={() => {handleOpenUserInfo(value)}} size='small'>
                <InfoIcon fontSize="inherit"/>
            </IconButton>
            <IconButton onClick={() => {}} size='small'>
                <BlockIcon fontSize="inherit"/>
            </IconButton>
            {userInfo && <UserModalInfo 
                isOpen={userInfo}
                handleCloseModal={handleCloseUserInfo}
                user={user.current}
            />
            }
        </>
    );
}