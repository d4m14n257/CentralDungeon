import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

import ModalBase from "@/components/ModalBase";

import { modal } from "@/styles/tables/modal";
import CreateTableForm from '@/forms/CreateTableForm';

export default function CreateModalTable (props) {
    const { isOpen, handleCloseModal } = props;

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        >
            <Box sx={modal.body}>
                <Box sx={modal.header}>
                    <Typography variant="h5">
                        Crear mesa
                    </Typography>
                </Box>
                <Box sx={modal.content}>
                    <CreateTableForm 
                        
                    />
                </Box>
                <Box sx={modal.footer}>

                </Box>
            </Box>
        </ModalBase>
    );
}