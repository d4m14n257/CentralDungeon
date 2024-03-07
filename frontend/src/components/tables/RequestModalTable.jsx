import { TextareaAutosize } from '@mui/base/TextareaAutosize';

import ModalBase from "../ModalBase";

export default function RequestModalTable (props) {
    const {isOpen, handleCloseModal} = props;

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        >
            <TextareaAutosize />
        </ModalBase>
    );
}