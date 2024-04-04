import { useRouter } from 'next/router';

import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';

import ModalBase from "@/components/general/ModalBase";

import { modal } from "@/styles/tables/modal";
import CreateTableForm from '@/forms/CreateTableForm';

export default function CreateModalTable (props) {
    const { isOpen, handleCloseModal } = props;
    const router = useRouter();

    const handleCreateTable = () => {
        router.push('/tables/1');
    }

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
                    <CreateTableForm />
                </Box>
                <Box sx={modal.footer}>
                    <Button variant='outlined' onClick={handleCreateTable}>Crear</Button>
                </Box>
            </Box>
        </ModalBase>
    );
}